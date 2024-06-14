package com.aarsh.cryptovid;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class LargeFilesHandler extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final int VIDEO_SELECT_CODE = 2;
    private static final int PART_SIZE = 15 * 1024 * 1024; // 15MB

    // Generate RSA key pair (public and private ke
    // Convert public and private keys to strings

// Use publicKeyString and privateKeyString in your encryption and decryption methods


    private Uri selectedVideoUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_large_files_handler);

        Button selectButton = findViewById(R.id.selectButton);
        Button splitButton = findViewById(R.id.splitButton);
        Button decryptButton = findViewById(R.id.decryptButton);

        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkAndRequestPermissions()) {
                    selectVideo();
                }
            }
        });

        splitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedVideoUri != null) {
                    splitVideo(selectedVideoUri);
                } else {
                    Toast.makeText(LargeFilesHandler.this, "Please select a video first.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        decryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Assuming the decryption process will be handled for files in a specific directory
                decryptAndMergeVideo();
            }
        });
    }

    private boolean checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void selectVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, VIDEO_SELECT_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VIDEO_SELECT_CODE && resultCode == RESULT_OK) {
            if (data != null) {
                selectedVideoUri = data.getData();
                Toast.makeText(this, "Video selected: " + selectedVideoUri, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void splitVideo(Uri videoUri) {
        ContentResolver contentResolver = getContentResolver();
        try (InputStream inputStream = contentResolver.openInputStream(videoUri)) {
            if (inputStream == null) {
                Log.e("VideoSplitter", "Input stream is null.");
                return;
            }

            String outputDirPath = getExternalFilesDir(null) + "/VideoParts";
            File outputDir = new File(outputDirPath);
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }

            byte[] buffer = new byte[PART_SIZE];
            int partNumber = 1;
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) > 0) {
                // Create a file for each part to generate a URI
                File outputFile = new File(outputDir, "part" + partNumber + ".mp4");
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(buffer, 0, bytesRead);
                }

                // Generate URI for the part and call samplefunc
                Uri partUri = Uri.fromFile(outputFile);
                compressAndConvertVideoToByteArray(partUri, partNumber);

                partNumber++;
            }

           // String outputDirPath = getExternalFilesDir(null) + "/VideoParts";
            mergeTextFiles(outputDirPath);

            Toast.makeText(this, "Video split into parts and processed successfully.", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            Log.e("VideoSplitter", "Error splitting video: " + e.getMessage());
        }
    }

    SecretKey key;
    int KEY_SIZE = 128;
    int T_LEN = 128;
    Cipher encipher;

    private void compressAndConvertVideoToByteArray(Uri videoUri, int part) {
        new Thread(() -> {
            try {
                // Initialize the encryption key
                init();

                // Convert the video to byte array
                byte[] videoByteArray = convertVideoFileToByteArray(videoUri);

                StringBuilder finalStringBuilder = new StringBuilder();

                // Get the public key
                String pubkey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDFfXJnodUspqqhA9Te8C59iRcV963Irh+ZldUN" +
                        "uSjMrr6UQUCiwLCvfGrGhHI8Q/tQS3gTgiGMM7kaVrzBLDTElBF6tj5fjFA8wp1brvTL5zjYHhWr" +
                        "Hp4UH/Uv4AIlyD5CnLUJs9CiT+V/6itQnw/5b/IV9sYaciUlzSDJvoRiQQIDAQAB";

                // Encrypt the video with AES
                byte[] encryptedVideo = encryptAES(videoByteArray);

                // Convert encrypted video to Base64 string
                String base64Video = Base64.encodeToString(encryptedVideo, Base64.DEFAULT);

                // Encrypt the AES key with RSA
                String keyString = convertSecretKeyToString(key);
                String encryptedKey = encryptRSA(keyString, pubkey);

                // Append identifiers, encrypted key, and encrypted video to final string
                finalStringBuilder.append("PART_").append(part).append("_AES_KEY:")
                        .append(encryptedKey).append("|").append("TestString").append("||");

                // Save the final string to a file
                saveStringToFile(finalStringBuilder.toString(),videoUri, part);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Video processed successfully", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                Log.e(TAG, "Error compressing video: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error compressing video", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during encryption: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error during encryption", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void mergeTextFiles(String directoryPath) {
        new Thread(() -> {
            try {
                File dir = new File(directoryPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    Log.e(TAG, "Directory does not exist or is not a directory.");
                    return;
                }

                // Get all files with the name starting with "output" and ending with ".txt"
                File[] files = dir.listFiles((dir1, name) -> name.startsWith("output") && name.endsWith(".txt"));

                if (files == null || files.length == 0) {
                    Log.e(TAG, "No text files found to merge.");
                    return;
                }

                // Sort the files by their numeric suffix
                // Sort the files by their numeric suffix
                Arrays.sort(files, (f1, f2) -> {
                    String name1 = f1.getName().replace("output", "").replace(".txt", "");
                    String name2 = f2.getName().replace("output", "").replace(".txt", "");
                    try {
                        int num1 = Integer.parseInt(name1);
                        int num2 = Integer.parseInt(name2);
                        return Integer.compare(num1, num2);
                    } catch (NumberFormatException e) {
                        return name1.compareTo(name2);
                    }
                });


                // Create the merged file in the Documents folder
                File mergedFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "merged_output.txt");
                try (FileOutputStream fos = new FileOutputStream(mergedFile);
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos))) {

                    for (File file : files) {
                        if (file.isFile() && file.canRead()) {
                            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    writer.write(line);
                                    writer.write(System.lineSeparator());
                                }
                                writer.write("==== END OF PART ====");
                                writer.write(System.lineSeparator());
                            } catch (IOException e) {
                                Log.e(TAG, "Error reading file: " + file.getName(), e);
                            }
                        } else {
                            Log.e(TAG, "Skipping file: " + file.getName() + " (cannot read or not a file)");
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error writing merged file", e);
                }

                Log.d(TAG, "Merged file saved at: " + mergedFile.getAbsolutePath());
                runOnUiThread(() -> {
                    Toast.makeText(this, "Text files merged successfully and saved in Documents folder", Toast.LENGTH_SHORT).show();
                });

                // Clear the folder after merging
                clearFolder(directoryPath);

            } catch (Exception e) {
                Log.e(TAG, "Error merging text files: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error merging text files", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void clearFolder(String directoryPath) {
        File dir = new File(directoryPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
        }
    }



    private byte[] convertVideoFileToByteArray(Uri videoUri) throws IOException {
        // Get content resolver to open input stream from URI
        ContentResolver contentResolver = getContentResolver();
        try (InputStream inputStream = contentResolver.openInputStream(videoUri);
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            if (inputStream == null) {
                throw new IOException("Unable to open input stream for URI: " + videoUri);
            }

            // Buffer to hold data chunks
            byte[] buffer = new byte[1024];
            int length;

            // Read data from input stream into byte array output stream
            while ((length = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }

            // Flush the byte array output stream
            byteArrayOutputStream.flush();

            // Return the byte array
            return byteArrayOutputStream.toByteArray();
        }
    }

    public byte[] encryptAES(byte[] msg) throws Exception {
        Cipher encipher = Cipher.getInstance("AES/GCM/NoPadding");
        encipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = encipher.getIV();
        byte[] enbytes = encipher.doFinal(msg);

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + enbytes.length);
        byteBuffer.put(iv);
        byteBuffer.put(enbytes);

        return byteBuffer.array();
    }

    private String convertSecretKeyToString(SecretKey key) {
        byte[] encodedKey = key.getEncoded();
        return Base64.encodeToString(encodedKey, Base64.DEFAULT);
    }

    private String encryptRSA(String data, String publicKeyString) {
        try {
            PublicKey publicKey = convertStringToPublicKey(publicKeyString);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedBytes = cipher.doFinal(data.getBytes());
            return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
        } catch (Exception e) {
            Log.e(TAG, "Error encrypting data with RSA: " + e.getMessage(), e);
            return null;
        }
    }

    private void saveStringToFile(String data, Uri uri, int i) {
        ContentResolver contentResolver = getContentResolver();
        try {
            // Extract directory path from the provided URI
            String videoFilePath = uri.getPath();
            File videoFile = new File(videoFilePath);
            String directoryPath = videoFile.getParent();

            // Create a new file in this directory
            File outputFile = new File(directoryPath, "output" + i + ".txt");

            // Write the data to the new file
            try (FileOutputStream fos = new FileOutputStream(outputFile);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos))) {
                writer.write(data);
            }

            // Create a new URI for the output file
            Uri outputUri = Uri.fromFile(outputFile);

            Log.d(TAG, "String saved to file: " + outputUri.getPath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving string to file: " + e.getMessage(), e);
        }
    }

    public void init() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_SIZE);
        key = generator.generateKey();
    }

    private PublicKey convertStringToPublicKey(String keyString) throws Exception {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private PrivateKey convertStringToPrivateKey(String keyString) throws Exception {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    public byte[] decryptAES(byte[] encryptedMsg, SecretKey key) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedMsg);
        byte[] iv = new byte[12];
        byteBuffer.get(iv);
        byte[] enbytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(enbytes);

        Cipher decipher = Cipher.getInstance("AES/GCM/NoPadding");
        decipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(T_LEN, iv));
        return decipher.doFinal(enbytes);
    }

    private String decryptRSA(String encryptedData, String privateKeyString) {
        try {
            PrivateKey privateKey = convertStringToPrivateKey(privateKeyString);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(Base64.decode(encryptedData, Base64.DEFAULT));
            return new String(decryptedBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting data with RSA: " + e.getMessage(), e);
            return null;
        }
    }

    private SecretKey convertStringToSecretKey(String keyString) {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }


    private void decryptAndMergeVideo() {
        new Thread(() -> {
            try {
                File mergedFile = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "merged_output.txt");
                if (!mergedFile.exists()) {
                    Log.e(TAG, "Merged file does not exist.");
                    return;
                }

                String privateKeyString = "MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBAMV9cmeh1SymqqED1N7wLn2JFxX3rciuH5mV1Q25KMyuvpRBQKLAsK98asaEcjxD+1BLeBOCIYwzuRpWvMEsNMSUEXq2Pl+MUDzCnVuu9MvnONgeFasenhQf9S/gAiXIPkKctQmz0KJP5X/qK1CfD/lv8hX2xhpyJSXNIMm+hGJBAgMBAAECgYAODGx0/sghUwHUTmd4LHHCMFxaDbJpEinhTQ1I2qESXmPkO/gTG831zc3yrT1LGDLqdVPnX1xvksZaH01yGGjwauOuQD/kw+CoqEsyie2SyfX34v4VXnmLc0YZW+SXeiPh67d/QK2EbZOI0GRu99LOqmdO0O/Te5nOD1uG9AwdWQJBAO7VjceWBAhYRzAWZ2DNKEMSt88b9ZCY3GDK+CNFcF1AWdB6ljP442PUsOMKjonEDf4cwSyJ80NxGdjm2Ac9cGcCQQDTry2fW1CnzYmsO+YTgKrOK4QV+nVG6fxJdU17nTlGyPiGTWXDA0kFU8esdF6RhRx4NF81t66ghSuhHLbDhM8XAkEA7RxG7ecZic9anXsglxIW7sAejBeN7EhWQiI/x4Sg0XOZt0h85owp9GqsUjug11U1LxsNDVLHmCUpLBXCUy3D8QJBAIj6SmNcC40KC5RQDkmAcQaIUiiGsWz57C78oO7khjOvyGHfo4HVlmLEG+kURD2WDR4bhaCVA4MLqXfPxNQwFHECQAP4XU/0+mtZDWi4l1hAh/N+UHcfwY6UNzM99vRnKRTGwDcNI172laV1yIb4BymR649fupNfzfpL9zeJzN2Sd/I="; // Add your private key string here
                StringBuilder decryptedStringBuilder = new StringBuilder();

                try (BufferedReader reader = new BufferedReader(new FileReader(mergedFile))) {
                    String line;

                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("PART_")) {
                            String[] parts = line.split("_AES_KEY:");
                            if (parts.length > 1) {
                                String[] keyAndChunk = parts[1].split("\\|");
                                if (keyAndChunk.length > 1) {
                                    String encryptedKey = keyAndChunk[0];
                                    String encryptedChunk = keyAndChunk[1];

                                    // Decrypt the AES key
                                    String decryptedKeyString = decryptRSA(encryptedKey, privateKeyString);
                                    SecretKey decryptedKey = convertStringToSecretKey(decryptedKeyString);

                                    // Decrypt the video chunk
                                    byte[] encryptedBytes = Base64.decode(encryptedChunk, Base64.DEFAULT);
                                    byte[] decryptedBytes = decryptAES(encryptedBytes, decryptedKey);

                                    // Append decrypted bytes to final string
                                    decryptedStringBuilder.append(new String(decryptedBytes, StandardCharsets.ISO_8859_1));
                                } else {
                                    Log.e(TAG, "Invalid key and chunk format: " + parts[1]);
                                }
                            } else {
                                Log.e(TAG, "Invalid line format: " + line);
                            }
                        }
                    }
                }

                // Save the decrypted data to a file
                saveDecryptedVideoToFile(decryptedStringBuilder.toString());

                runOnUiThread(() -> {
                    Toast.makeText(this, "Video decrypted and merged successfully", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                Log.e(TAG, "Error reading merged file: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error reading merged file", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during decryption: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error during decryption", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }




    private void saveDecryptedVideoToFile(String decryptedData) throws IOException {
        File outputDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "DecryptedVideos");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputFile = new File(outputDir, "decrypted_video.mp4");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            fos.write(decryptedData.getBytes(StandardCharsets.ISO_8859_1));
        }

        Log.d(TAG, "Decrypted video saved to: " + outputFile.getAbsolutePath());
    }
}
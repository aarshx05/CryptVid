package com.aarsh.cryptovid;
//Commit
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
import java.io.FileWriter;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class LargeFilesHandler extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String DELIMITER = "PART_";
    private static final int VIDEO_SELECT_CODE = 2;
    private static final int PART_SIZE = 15 * 1024 * 1024; // 15MB

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
                    String directoryPath = getExternalFilesDir(null) + "/VideoParts";
                    clearDirectory(directoryPath);
                } else {
                    Toast.makeText(LargeFilesHandler.this, "Please select a video first.", Toast.LENGTH_SHORT).show();
                }
            }
        });

        decryptButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

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

            mergeTextFiles(outputDirPath);

            Toast.makeText(this, "Video split into parts and processed successfully.", Toast.LENGTH_SHORT).show();


        } catch (IOException e) {
            Log.e("VideoSplitter", "Error splitting video: " + e.getMessage());
        }
    }

    SecretKey key;

    private void compressAndConvertVideoToByteArray(Uri videoUri, int part) {

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

                // Encrypt the video with DES
                byte[] encryptedVideo = encryptDES(videoByteArray);

                // Convert encrypted video to Base64 string
                String base64Video = Base64.encodeToString(encryptedVideo, Base64.DEFAULT);

                // Encrypt the DES key with RSA
                String keyString = convertSecretKeyToString(key);
                String encryptedKey = encryptRSA(keyString, pubkey);

                // Append identifiers, encrypted key, and encrypted video to final string
                finalStringBuilder.append("PART_").append(part).append("_DES_KEY:")
                        .append(encryptedKey).append("|").append(base64Video).append("||");

                // Save the final string to a file
                saveStringToFile(finalStringBuilder.toString(), videoUri, part);

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

    }

    private void mergeTextFiles(String directoryPath) {

            try {
                File dir = new File(directoryPath);
                if (!dir.exists() || !dir.isDirectory()) {
                    Log.e(TAG, "Directory does not exist or is not a directory: " + directoryPath);
                    return;
                }

                // Get all files with the name starting with "output" and ending with ".txt"
                File[] files = dir.listFiles((dir1, name) -> name.startsWith("output") && name.endsWith(".txt"));

                if (files == null || files.length == 0) {
                    Log.e(TAG, "No text files found to merge in directory: " + directoryPath);
                    return;
                }

                // Sort the files by their numeric suffix
                Arrays.sort(files, (f1, f2) -> {
                    String name1 = f1.getName().replace("output", "").replace(".txt", "");
                    String name2 = f2.getName().replace("output", "").replace(".txt", "");
                    try {
                        int num1 = Integer.parseInt(name1);
                        int num2 = Integer.parseInt(name2);
                        return Integer.compare(num1, num2);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Error parsing file names to numbers: " + name1 + ", " + name2, e);
                        return name1.compareTo(name2);
                    }
                });

                // Get the path to the Documents directory
                File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
                if (!documentsDir.exists()) {
                    documentsDir.mkdirs(); // Create the Documents directory if it doesn't exist
                }

                File mergedFile = new File(documentsDir, "final_output.txt");
                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(mergedFile, true)))) {
                    for (File file : files) {
                        Log.d(TAG, "Merging file: " + file.getName());
                        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                writer.write(line);
                                writer.newLine();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error reading file: " + file.getName(), e);
                        }
                    }
                }

                Log.d(TAG, "Merged file created at: " + mergedFile.getAbsolutePath());

                runOnUiThread(() -> {
                    Toast.makeText(this, "Text files merged successfully.", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                Log.e(TAG, "Error merging text files", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error merging text files", Toast.LENGTH_SHORT).show();
                });
            }

    }

    private void clearDirectory(String directoryPath) {
        File directory = new File(directoryPath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        deleteFileWithRetry(file, 3);
                    }
                }
            }
            Log.d(TAG, "Directory cleared: " + directoryPath);
        } else {
            Log.e(TAG, "Directory does not exist or is not a directory: " + directoryPath);
        }
    }

    private void deleteFileWithRetry(File file, int retryCount) {
        boolean deleted = false;
        int attempts = 0;
        while (!deleted && attempts < retryCount) {
            deleted = file.delete();
            attempts++;
            if (!deleted) {
                Log.e(TAG, "Failed to delete file: " + file.getAbsolutePath() + ", attempt: " + attempts);
                try {
                    Thread.sleep(100); // Wait for a short period before retrying
                } catch (InterruptedException e) {
                    Log.e(TAG, "Thread interrupted while waiting to retry file deletion", e);
                }
            }
        }
        if (!deleted) {
            Log.e(TAG, "Failed to delete file after " + retryCount + " attempts: " + file.getAbsolutePath());
        }
    }




    private void init() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("DES");
        keyGen.init(56); // DES key size is 56 bits
        key = keyGen.generateKey();
    }

    private byte[] encryptDES(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    private String encryptRSA(String data, String publicKey) throws Exception {
        byte[] publicBytes = Base64.decode(publicKey, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(publicBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey pubKey = keyFactory.generatePublic(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);

        byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT);
    }

    private String convertSecretKeyToString(SecretKey secretKey) {
        return Base64.encodeToString(secretKey.getEncoded(), Base64.DEFAULT);
    }

    private byte[] convertVideoFileToByteArray(Uri videoUri) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = getContentResolver().openInputStream(videoUri)) {
            if (inputStream != null) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, len);
                }
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void saveStringToFile(String data, Uri videoUri, int part) throws IOException {
        String outputDirPath = getExternalFilesDir(null) + "/VideoParts";
        File outputDir = new File(outputDirPath);
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        File outputFile = new File(outputDir, "output" + part + ".txt");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(data);
        }
    }

    private void decryptAndMergeVideo() {
        new Thread(() -> {
            String outputDirPath = getExternalFilesDir(null) + "/VideoParts"+"/final_output.txt";
            splitFile(outputDirPath);

        }).start();
    }


    private void splitFile(String filePath) {
        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            Log.e(TAG, "Input file does not exist");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            String line;
            int fileCount = 1;
            StringBuilder contentBuilder = new StringBuilder();

            while ((line = reader.readLine()) != null) {
                if (line.startsWith(DELIMITER)) {
                    if (contentBuilder.length() > 0) {
                        writeToFile(contentBuilder.toString(), fileCount++);
                        contentBuilder.setLength(0);
                    }
                }
                contentBuilder.append(line.replace("||", "")).append(System.lineSeparator());
            }

            // Write the last part if exists
            if (contentBuilder.length() > 0) {
                writeToFile(contentBuilder.toString(), fileCount);
            }

        } catch (IOException e) {
            Log.e(TAG, "Error reading file", e);
        }
    }

    private void writeToFile(String content, int fileNumber) {
        File outputFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "newoutput " + fileNumber + ".txt");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
            writer.write(content);
            Log.i(TAG, "Written to " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error writing file", e);
        }
    }


    private String decryptRSA(String data, String privateKey) throws Exception {
        byte[] privateBytes = Base64.decode(privateKey, Base64.DEFAULT);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privKey = keyFactory.generatePrivate(keySpec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privKey);

        byte[] decryptedBytes = cipher.doFinal(Base64.decode(data, Base64.DEFAULT));
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    private byte[] decryptDES(byte[] data, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("DES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }
}

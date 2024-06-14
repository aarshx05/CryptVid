package com.aarsh.cryptovid;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_READ_EXTERNAL_STORAGE = 100;
    private static final int PICK_VIDEO_REQUEST = 1;
    private static final int PICK_TEXT_FILE_REQUEST = 2;
    private static final int CREATE_VIDEO_FILE_REQUEST = 3;
    private static final int CREATE_TEXT_FILE_REQUEST = 4;
    private static final String TAG = "MainActivity";
    private static final String KEY_ALIAS = "my_key_alias";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private TextView textView;
    private TextView largeFiles;
    private KeyStore keyStore;
    private Cipher cipher;
    private ImageView selectVideoButton;
    private Button convertToByteArrayButton;
    private Button convertToVideoButton;
    private ImageView selectTextFileButton;
    private ProgressBar progressBar;
    private Uri selectedVideoUri;
    private Uri selectedTextFileUri;
    private Uri createFileUri;
    private EditText keytxt;

    private ImageView generateRSAKeyPairButton;
    private PublicKey rsaPublicKey;
    private PrivateKey rsaPrivateKey;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        selectVideoButton = findViewById(R.id.selectVideoButton);
        convertToByteArrayButton = findViewById(R.id.convertToByteArrayButton);
        convertToVideoButton = findViewById(R.id.convertToVideoButton);
        selectTextFileButton = findViewById(R.id.selectTextFileButton);
        progressBar = findViewById(R.id.progressBar);
        largeFiles=findViewById(R.id.largerFiles);
        keytxt = findViewById(R.id.genKey);
        generateRSAKeyPairButton = findViewById(R.id.generateRSAButton);

        // Check for READ_EXTERNAL_STORAGE permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL_STORAGE);
        } else {
            handleIncomingIntent();
        }

        largeFiles.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, LargeFilesHandler.class);
                startActivity(intent);
            }
        });

        // Set OnClickListener to clear EditText's text when clicked
        keytxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                keytxt.setText("");
            }
        });

        // Set OnFocusChangeListener to clear EditText's text when focused
        keytxt.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    keytxt.setText("");
                }
            }
        });

        generateRSAKeyPairButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, rsakeys.class);
                startActivity(intent);
            }
        });

        selectVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser(PICK_VIDEO_REQUEST, "video/*");
            }
        });

        convertToByteArrayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedVideoUri != null) {
                    createFileChooser(CREATE_TEXT_FILE_REQUEST, "text/*", "video_base64.txt");
                } else {
                    Toast.makeText(MainActivity.this, "No video selected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        convertToVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectedTextFileUri != null) {
                    createFileChooser(CREATE_VIDEO_FILE_REQUEST, "video/*", "sample.mp4");
                } else {
                    Toast.makeText(MainActivity.this, "No text file selected", Toast.LENGTH_SHORT).show();
                }
            }
        });

        selectTextFileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileChooser(PICK_TEXT_FILE_REQUEST, "text/*");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                handleIncomingIntent();
            } else {
                Toast.makeText(this, "Permission denied to read external storage", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleIncomingIntent() {
        Intent intent = getIntent();
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            Uri fileUri = intent.getData();
            handleJacFile(fileUri);
        }
    }

    private void handleJacFile(Uri fileUri) {
        if (fileUri != null) {
            selectedTextFileUri = fileUri;
            Toast.makeText(this, "Text file selected: " + fileUri.getPath(), Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Error: .jac file URI is null", Toast.LENGTH_SHORT).show();
        }
    }




    private void openFileChooser(int requestCode, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (requestCode == PICK_TEXT_FILE_REQUEST) {
            // To allow the selection of .jac files, use "*/*" as the MIME type
            intent.setType("*/*");
            String[] mimeTypes = {"text/plain", "application/octet-stream"};
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        }
        startActivityForResult(intent, requestCode);
    }


    private void createFileChooser(int requestCode, String mimeType, String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Change the file extension from .txt to .jac
        if (requestCode == CREATE_TEXT_FILE_REQUEST) {
            fileName = fileName.replace(".txt", ".jac");
        }
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, requestCode);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == PICK_VIDEO_REQUEST) {
                selectedVideoUri = uri;
                Toast.makeText(this, "Video selected: " + uri.getPath(), Toast.LENGTH_SHORT).show();
            } else if (requestCode == CREATE_TEXT_FILE_REQUEST) {
                createFileUri = uri;
                compressAndConvertVideoToByteArray(selectedVideoUri);
            } else if (requestCode == PICK_TEXT_FILE_REQUEST) {
                String uriString = uri.toString();
                //if (uriString.endsWith(".jac")) {
                    selectedTextFileUri = uri;
               //     Toast.makeText(this, "Text file selected: " + uri.getPath(), Toast.LENGTH_SHORT).show();
                //} //else {
                 //   Toast.makeText(this, "Please select a .jac file", Toast.LENGTH_SHORT).show();
                //}
            } else if (requestCode == CREATE_VIDEO_FILE_REQUEST) {
                createFileUri = uri;
                convertBase64StringToVideo();
            }
        }
    }




    private void showProgressBar() {
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        progressBar.setVisibility(View.GONE);
    }

    SecretKey key;
    int KEY_SIZE = 128;
    int T_LEN = 128;
    Cipher encipher;



    private void compressAndConvertVideoToByteArray(Uri videoUri) {
        showProgressBar();
        Log.d(TAG,"Started");
        new Thread(() -> {
            try {
                // Initialize the encryption key
                init();

                // Create a temporary file for the compressed video
                File compressedFile = new File(getCacheDir(), "compressed_video.mp4");

                // Compress the video
                compressVideo(videoUri, compressedFile.getAbsolutePath());

                // Convert the compressed video to byte array
                byte[] videoByteArray = convertVideoFileToByteArray(compressedFile);

                // Define the chunk size (e.g., 1 MB)
                int chunkSize = 1024 * 1024;
                int numberOfChunks = (int) Math.ceil((double) videoByteArray.length / chunkSize);
                StringBuilder finalStringBuilder = new StringBuilder();

                // Get the public key
                String pubkey = keytxt.getText().toString();

                // Encrypt each chunk
                for (int i = 0; i < numberOfChunks; i++) {
                    int start = i * chunkSize;
                    int end = Math.min(videoByteArray.length, start + chunkSize);
                    byte[] chunk = Arrays.copyOfRange(videoByteArray, start, end);

                    // Encrypt the chunk with AES
                    byte[] encryptedChunk = encryptAES(chunk);

                    // Convert encrypted chunk to Base64 string
                    String base64Chunk = Base64.encodeToString(encryptedChunk, Base64.DEFAULT);

                    // Encrypt the AES key with RSA
                    String keyString = convertSecretKeyToString(key);
                    String encryptedKey = encryptRSA(keyString, pubkey);

                    // Append identifiers, encrypted key, and encrypted chunk to final string
                    finalStringBuilder.append("PART_").append(i + 1).append("_AES_KEY:")
                            .append(encryptedKey).append("|").append(base64Chunk).append("||");
                }

                // Save the final string to a file
                saveStringToFile(finalStringBuilder.toString(), createFileUri);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Video processed successfully", Toast.LENGTH_SHORT).show();
                });

                // Clean up temporary file
                compressedFile.delete();
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
            } finally {
                Log.d(TAG,"Ended");
                runOnUiThread(this::hideProgressBar);
            }
        }).start();
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

    private PublicKey convertStringToPublicKey(String keyString) throws Exception {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private String convertSecretKeyToString(SecretKey key) {
        byte[] encodedKey = key.getEncoded();
        return Base64.encodeToString(encodedKey, Base64.DEFAULT);
    }

    public void init() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(KEY_SIZE);
        key = generator.generateKey();
    }

    public byte[] decryptAES(byte[] enmsg, String encryptedkey) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(enmsg);
        byte[] iv = new byte[12]; // GCM standard IV length is 12 bytes
        byteBuffer.get(iv);

        byte[] enbytes = new byte[byteBuffer.remaining()];
        byteBuffer.get(enbytes);

        String priv = keytxt.getText().toString();
        String finalkey = decryptRSA(encryptedkey, priv);

        SecretKey skey = convertStringToSecretKey(finalkey);
        Cipher deCipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(T_LEN, iv);
        deCipher.init(Cipher.DECRYPT_MODE, skey, spec);

        return deCipher.doFinal(enbytes);
    }

    private String decryptRSA(String encryptedData, String privateKeyString) {
        try {
            PrivateKey privateKey = convertStringToPrivateKey(privateKeyString);
            byte[] encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error decrypting data with RSA: " + e.getMessage(), e);
            return null;
        }
    }

    private PrivateKey convertStringToPrivateKey(String keyString) throws Exception {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decodedKey);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(spec);
    }

    public SecretKey convertStringToSecretKey(String keyString) {
        byte[] decodedKey = Base64.decode(keyString, Base64.DEFAULT);
        return new SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");
    }

    public byte[] encryptAES(byte[] msg) throws Exception {
        encipher = Cipher.getInstance("AES/GCM/NoPadding");
        encipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] iv = encipher.getIV();
        byte[] enbytes = encipher.doFinal(msg);

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + enbytes.length);
        byteBuffer.put(iv);
        byteBuffer.put(enbytes);

        return byteBuffer.array();
    }


    private void compressVideo(Uri inputUri, String outputPath) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(getContentResolver().openAssetFileDescriptor(inputUri, "r").getFileDescriptor());

        MediaMuxer muxer = null;
        try {
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int trackIndex = -1;
            int frameRate = 30;

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);

                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    MediaFormat outputFormat = MediaFormat.createVideoFormat(mime, format.getInteger(MediaFormat.KEY_WIDTH), format.getInteger(MediaFormat.KEY_HEIGHT));
                    outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, 2000000); // 2Mbps
                    outputFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
                    outputFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

                    if (format.containsKey("csd-0")) {
                        outputFormat.setByteBuffer("csd-0", format.getByteBuffer("csd-0"));
                    }
                    if (format.containsKey("csd-1")) {
                        outputFormat.setByteBuffer("csd-1", format.getByteBuffer("csd-1"));
                    }

                    trackIndex = muxer.addTrack(outputFormat);
                    muxer.start();

                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    ByteBuffer buffer = ByteBuffer.allocate(1048576);

                    while (true) {
                        bufferInfo.offset = 0;
                        bufferInfo.size = extractor.readSampleData(buffer, 0);

                        if (bufferInfo.size < 0) {
                            bufferInfo.size = 0;
                            break;
                        } else {
                            bufferInfo.presentationTimeUs = extractor.getSampleTime();
                            bufferInfo.flags = extractor.getSampleFlags();
                            muxer.writeSampleData(trackIndex, buffer, bufferInfo);
                            extractor.advance();
                        }
                    }

                    break;
                }
            }

        } finally {
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error stopping muxer: " + e.getMessage(), e);
                }
                muxer.release();
            }
            extractor.release();
        }
    }

    private byte[] convertVideoFileToByteArray(File videoFile) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(Uri.fromFile(videoFile));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, length);
        }
        byteArrayOutputStream.flush();
        inputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

    private void saveStringToFile(String data, Uri uri) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(data);
            writer.close();
            shareFileViaWhatsApp(uri);
            Log.d(TAG, "String saved to file: " + uri.getPath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving string to file: " + e.getMessage(), e);
        }
    }


    private void shareFileViaWhatsApp(Uri uri) {
        // Create an Intent to share the file via WhatsApp
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain"); // Set the MIME type of the file
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri); // Attach the file URI
        shareIntent.setPackage("com.whatsapp"); // Set the package to WhatsApp

        // Verify if WhatsApp is installed on the device
        if (shareIntent.resolveActivity(getPackageManager()) != null) {
            // Start the Intent on the UI thread
            runOnUiThread(() -> startActivity(shareIntent));
        } else {
            // WhatsApp is not installed, show a Toast message on the UI thread
            runOnUiThread(() -> Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show());
        }
    }




    private void convertBase64StringToVideo() {
        showProgressBar();
        new Thread(() -> {
            try {
                String base64String = readStringFromFile(selectedTextFileUri);

                // Split the base64 string into parts using the delimiter "||"
                String[] parts = base64String.split("\\|\\|");

                // Prepare a byte array output stream to collect the decrypted video data
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                for (String part : parts) {
                    if (part.isEmpty()) {
                        continue;
                    }

                    // Find the part identifier and the encrypted key
                    int separatorIndex = part.indexOf("|");
                    if (separatorIndex == -1) {
                        throw new IllegalArgumentException("Invalid format: Encrypted key and Base64 string separator not found");
                    }

                    String partIdentifierAndKey = part.substring(0, separatorIndex);
                    String base64VideoChunk = part.substring(separatorIndex + 1);

                    // Extract the encrypted AES key from the part
                    String encryptedKey = partIdentifierAndKey.substring(partIdentifierAndKey.indexOf("_AES_KEY:") + "_AES_KEY:".length());

                    // Decode the Base64 video chunk
                    byte[] encryptedChunk = Base64.decode(base64VideoChunk, Base64.DEFAULT);

                    // Decrypt the chunk using the provided decryptAES method
                    byte[] decryptedChunk = decryptAES(encryptedChunk, encryptedKey);

                    // Write the decrypted chunk to the output stream
                    byteArrayOutputStream.write(decryptedChunk);
                }

                // Convert the collected byte data into a byte array
                byte[] finalVideoBytes = byteArrayOutputStream.toByteArray();

                // Save the final byte array as a video file
                saveByteArrayAsVideo(finalVideoBytes, createFileUri);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Base64 string converted to video and saved as sample.mp4", Toast.LENGTH_SHORT).show();
                });
            } catch (IOException e) {
                Log.e(TAG, "Error converting Base64 string to video: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error converting Base64 string to video", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error during decryption: " + e.getMessage(), e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error during decryption", Toast.LENGTH_SHORT).show();
                });
            } finally {
                runOnUiThread(this::hideProgressBar);
            }
        }).start();
    }



    private String readStringFromFile(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            stringBuilder.append(line);
        }
        reader.close();
        return stringBuilder.toString();
    }

    private void saveByteArrayAsVideo(byte[] videoBytes, Uri uri) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            outputStream.write(videoBytes);
            outputStream.close();
            Log.d(TAG, "Byte array saved as video to file: " + uri.getPath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving byte array as video: " + e.getMessage(), e);
        }
    }
}

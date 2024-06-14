package com.aarsh.cryptovid;

import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.concurrent.Executor;

public class rsakeys extends AppCompatActivity {
    private Button rsa;
    private TextView pub;
    private TextView priv;


    private Executor executor;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rsakeys);

        rsa = findViewById(R.id.generateRSA);
        pub = findViewById(R.id.public_tv);
        priv = findViewById(R.id.priv_tv);

        // Initialize BiometricPrompt
        executor = ContextCompat.getMainExecutor(this);
        // Set OnClickListener for public key TextView
        pub.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTextToClipboard(pub.getText().toString());
                showToast("Public key copied to clipboard");
            }
        });

        // Set OnClickListener for private key TextView
        priv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyTextToClipboard(priv.getText().toString());
                showToast("Private key copied to clipboard");
            }
        });
        biometricPrompt = new BiometricPrompt(rsakeys.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showToast("Authentication error: " + errString);
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // Authentication successful, display or generate RSA keys
                handleAuthenticationSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                showToast("Authentication failed");
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Authenticate to view RSA keys")
                .setNegativeButtonText("Cancel")
                .build();

        rsa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BiometricManager biometricManager = BiometricManager.from(rsakeys.this);
                switch (biometricManager.canAuthenticate()) {
                    case BiometricManager.BIOMETRIC_SUCCESS:
                        biometricPrompt.authenticate(promptInfo);
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                        // Biometric authentication not available on this device
                        showToast("Biometric authentication not available");
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                        // Biometric hardware is unavailable
                        showToast("Biometric hardware is unavailable");
                        break;
                    case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                        // No biometric profiles enrolled on this device
                        // Provide a fallback mechanism to access RSA keys directly
                        handleFallback();
                        break;
                }
            }
        });
    }

    private void handleAuthenticationSuccess() {
        try {
            String[] keys = retrieveStrings();
            if (keys != null && keys.length == 2) {
                pub.setText(keys[0]);
                priv.setText(keys[1]);
            } else {
                String[] newKeys = generateRSAKeyPair();
                saveStrings(newKeys[0], newKeys[1]);
                pub.setText(newKeys[0]);
                priv.setText(newKeys[1]);
            }
        } catch (Exception e) {
            showToast("Error generating or retrieving RSA key pair: " + e.getMessage());
        }
    }


    private void copyTextToClipboard(String text) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("RSA Key", text);
        clipboard.setPrimaryClip(clip);
    }

    private String[] generateRSAKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PublicKey rsaPublicKey = keyPair.getPublic();
        PrivateKey rsaPrivateKey = keyPair.getPrivate();

        // Convert keys to Base64 encoded strings
        String publicKeyString = Base64.encodeToString(rsaPublicKey.getEncoded(), Base64.DEFAULT);
        String privateKeyString = Base64.encodeToString(rsaPrivateKey.getEncoded(), Base64.DEFAULT);

        return new String[]{publicKeyString, privateKeyString};
    }

    private void saveStrings(String publicKey, String privateKey) {
        // Store keys in SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences("RSAKeys", MODE_PRIVATE).edit();
        editor.putString("publicKey", publicKey);
        editor.putString("privateKey", privateKey);
        editor.apply();
    }

    private String[] retrieveStrings() {
        SharedPreferences sharedPreferences = getSharedPreferences("RSAKeys", MODE_PRIVATE);
        String publicKey = sharedPreferences.getString("publicKey", null);
        String privateKey = sharedPreferences.getString("privateKey", null);

        if (publicKey != null && privateKey != null) {
            return new String[]{publicKey, privateKey};
        }
        return null;
    }

    private void handleFallback() {
        // Provide a fallback mechanism to access RSA keys directly
        showToast("No biometric profiles enrolled on this device");
        try {
            String[] keys = retrieveStrings();
            if (keys != null && keys.length == 2) {
                pub.setText(keys[0]);
                priv.setText(keys[1]);
            } else {
                String[] newKeys = generateRSAKeyPair();
                saveStrings(newKeys[0], newKeys[1]);
                pub.setText(newKeys[0]);
                priv.setText(newKeys[1]);
            }
        } catch (Exception e) {
            showToast("Error generating or retrieving RSA key pair: " + e.getMessage());
        }
        // Implement your fallback mechanism here
        // For example, you could navigate to another activity where the keys are displayed without authentication
    }

    private void showToast(String message) {
        Toast.makeText(rsakeys.this, message, Toast.LENGTH_SHORT).show();
    }
}

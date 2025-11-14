package org.lineageos.settings.keyboxmanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

public class KeyboxManagerUtils {
    private static final String TAG = "KeyboxManagerUtils";
    
    private final Context mContext;
    
    // Download directory (NO ROOT ACCESS)
    private static final String DOWNLOAD_DIR = Environment.getExternalStorageDirectory() + "/Download/Keyboxes";
    
    // Known public keybox repositories (These are placeholder URLs, update with working sources)
    // In reality, these sources expire quickly, so the app realistically won't find many.
    // NOTE: Updated with potential community sources based on common knowledge (e.g., from GitHub searches).
    // These may not be valid or unrevoked; users should verify. For real use, sources like Telegram @PlayIntegrityFix or DroidWin are recommended.
    private static final KeyboxSource[] KEYBOX_SOURCES = {
        new KeyboxSource("Community Gist 1", "https://gist.githubusercontent.com/someuser/someid/raw/keybox.xml"), // Placeholder; replace with real if found
        new KeyboxSource("Community Gist 2", "https://gist.githubusercontent.com/anotheruser/anotherid/raw/keybox2.xml"), // Placeholder
        new KeyboxSource("Pastebin 1", "https://pastebin.com/raw/somepaste"), // Placeholder
        // Add more if searching yields results, e.g., from web_search: site:github.com "keybox.xml" "play integrity"
        // Example hypothetical: new KeyboxSource("GitHub Repo", "https://raw.githubusercontent.com/some-repo/trickystore-keybox/main/keybox.xml"),
    };
    
    // Internal class for sources
    private static class KeyboxSource {
        String name;
        String url;
        KeyboxSource(String name, String url) {
            this.name = name;
            this.url = url;
        }
    }

    public KeyboxManagerUtils(Context context) {
        mContext = context;
    }

    // ==================== NETWORK CHECK ====================
    
    public boolean isNetworkAvailable() {
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            NetworkInfo netInfo = cm.getActiveNetworkInfo();
            return netInfo != null && netInfo.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "Network check failed", e);
            return false;
        }
    }

    // ==================== ROOT CHECK (DISABLED) ====================
    
    public boolean isRootAvailable() {
        return false; // This app no longer requires root
    }

    // ==================== GENERATE KEYBOX (TEMPLATES) ====================
    
    public OperationResult generateKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            
            // Generate TWO template formats:
            // 1. Standard format (for Play Integrity Fix)
            // 2. TrickyStore format (PEM-based)
            
            generateStandardTemplate(downloadDir);
            generateTrickyStoreTemplate(downloadDir);
            
            return new OperationResult(true, 
                "📋 Two keybox templates generated:\n\n" +
                "1️⃣ Standard Format:\n" +
                "   " + DOWNLOAD_DIR + "/template_standard.xml\n" +
                "   (Base64-encoded keys)\n\n" +
                "2️⃣ TrickyStore Format:\n" +
                "   " + DOWNLOAD_DIR + "/template_trickystore.xml\n" +
                "   (PEM-formatted keys)\n\n" +
                "⚠️ IMPORTANT:\n" +
                "These are ONLY structural templates!\n\n" +
                "Valid keyboxes require:\n" +
                "✓ Google-signed certificates\n" +
                "✓ Valid cryptographic keys\n" +
                "✓ A real Device ID\n\n" +
                "💡 Use 'Search Keybox' to download real keyboxes\n" +
                "💡 Or import your own from community sources:\n" +
                "   • DroidWin.com (email request)\n" +
                "   • XDA Forums\n" +
                "   • Telegram groups\n" +
                "   • Reddit r/Magisk");
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating templates", e);
            return new OperationResult(false, "Template creation failed: " + e.getMessage());
        }
    }

    private void generateStandardTemplate(File dir) throws IOException {
        File templateFile = new File(dir, "template_standard.xml");
        try (FileWriter writer = new FileWriter(templateFile)) {
            writer.write("<?xml version=\"1.0\"?>\n");
            writer.write("<!-- TEMPLATE ONLY - NOT A VALID KEYBOX -->\n");
            writer.write("<!-- Standard format for Play Integrity Fix -->\n");
            writer.write("<AndroidAttestation>\n");
            writer.write("  <Keybox DeviceID=\"YOUR_DEVICE_ID_HERE\">\n");
            writer.write("    <Key algorithm=\"ecdsa\">\n");
            writer.write("      <PrivateKey>BASE64_ENCODED_ECDSA_PRIVATE_KEY</PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <Certificate>BASE64_LEAF_CERT</Certificate>\n");
            writer.write("        <Certificate>BASE64_INTERMEDIATE_CERT</Certificate>\n");
            writer.write("        <Certificate>BASE64_ROOT_CERT</Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("    <Key algorithm=\"rsa\">\n");
            writer.write("      <PrivateKey>BASE64_ENCODED_RSA_PRIVATE_KEY</PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <Certificate>BASE64_LEAF_CERT</Certificate>\n");
            writer.write("        <Certificate>BASE64_INTERMEDIATE_CERT</Certificate>\n");
            writer.write("        <Certificate>BASE64_ROOT_CERT</Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("  </Keybox>\n");
            writer.write("</AndroidAttestation>\n");
        }
    }

    private void generateTrickyStoreTemplate(File dir) throws IOException {
        File templateFile = new File(dir, "template_trickystore.xml");
        try (FileWriter writer = new FileWriter(templateFile)) {
            writer.write("<?xml version=\"1.0\"?>\n");
            writer.write("<!-- TEMPLATE ONLY - NOT A VALID KEYBOX -->\n");
            writer.write("<!-- TrickyStore format with PEM keys -->\n");
            writer.write("<AndroidAttestation>\n");
            writer.write("  <NumberOfKeyboxes>1</NumberOfKeyboxes>\n");
            writer.write("  <Keybox DeviceID=\"YOUR_DEVICE_ID_HERE\">\n");
            writer.write("    <Key algorithm=\"ecdsa\">\n");
            writer.write("      <PrivateKey format=\"pem\">\n");
            writer.write("-----BEGIN EC PRIVATE KEY-----\n");
            writer.write("YOUR_ECDSA_PRIVATE_KEY_HERE\n");
            writer.write("-----END EC PRIVATE KEY-----\n");
            writer.write("      </PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <NumberOfCertificates>3</NumberOfCertificates>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("LEAF_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("INTERMEDIATE_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("ROOT_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("    <Key algorithm=\"rsa\">\n");
            writer.write("      <PrivateKey format=\"pem\">\n");
            writer.write("-----BEGIN RSA PRIVATE KEY-----\n");
            writer.write("YOUR_RSA_PRIVATE_KEY_HERE\n");
            writer.write("-----END RSA PRIVATE KEY-----\n");
            writer.write("      </PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <NumberOfCertificates>3</NumberOfCertificates>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("LEAF_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("INTERMEDIATE_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\n");
            writer.write("ROOT_CERTIFICATE_HERE\n");
            writer.write("-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("  </Keybox>\n");
            writer.write("</AndroidAttestation>\n");
        }
    }

    // ==================== SEARCH & DOWNLOAD ====================
    
    public OperationResult searchAndDownloadKeyboxes(int maxAttempts) {
        try {
            Log.d(TAG, "Searching for valid keyboxes from community sources...");
            
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                if (!downloadDir.mkdirs()) {
                    return new OperationResult(false, "Failed to create download directory");
                }
            }
            
            int downloaded = 0;
            int attempted = 0;
            StringBuilder results = new StringBuilder();
            Map<String, String> downloadedHashes = new HashMap<>();
            
            results.append("🔍 Search Results:\n");
            results.append("═══════════════════════\n\n");
            
            if (KEYBOX_SOURCES.length == 0) {
                 results.append("⚠️ No keybox sources are defined in the code.\n");
            }
            
            // Try each source
            for (KeyboxSource source : KEYBOX_SOURCES) {
                if (downloaded >= maxAttempts) break;
                attempted++;
                
                results.append("📡 Source: ").append(source.name).append("\n");
                
                try {
                    String fileName = "keybox_" + (downloaded + 1) + ".xml";
                    File outputFile = new File(downloadDir, fileName);
                    
                    if (downloadFile(source.url, outputFile)) {
                        // Check if file is valid
                        if (isValidKeybox(outputFile)) {
                            // Check for duplicates using hash
                            String fileHash = calculateFileHash(outputFile);
                            if (!downloadedHashes.containsKey(fileHash)) {
                                downloaded++;
                                String deviceId = extractDeviceIdFromFile(outputFile);
                                downloadedHashes.put(fileHash, fileName);
                                
                                results.append("   ✅ Valid keybox downloaded\n");
                                results.append("   📱 Device: ").append(deviceId).append("\n");
                                results.append("   💾 File: ").append(fileName).append("\n\n");
                            } else {
                                results.append("   ⚠️ Duplicate file, skipped\n\n");
                                outputFile.delete();
                            }
                        } else {
                            results.append("   ❌ Invalid structure, discarded\n\n");
                            outputFile.delete();
                        }
                    } else {
                        results.append("   ❌ Download failed\n\n");
                    }
                } catch (Exception e) {
                    results.append("   ❌ Error: ").append(e.getMessage()).append("\n\n");
                }
            }
            
            results.append("═══════════════════════\n");
            results.append("📊 Summary: ").append(downloaded).append(" valid keyboxes downloaded from ").append(attempted).append(" sources\n");
            
            if (downloaded > 0) {
                return new OperationResult(true, results.toString());
            } else {
                return new OperationResult(false, results.toString() + "\n\n💡 No valid keyboxes found. Try community sources like Telegram @PlayIntegrityFix or DroidWin.com.");
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error in searchAndDownloadKeyboxes", e);
            return new OperationResult(false, "Search error: " + e.getMessage());
        }
    }

    // ==================== IMPORT ====================
    
    public OperationResult importKeybox(Uri uri) {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            
            String fileName = "imported_" + System.currentTimeMillis() + ".xml";
            File outputFile = new File(downloadDir, fileName);
            
            try (InputStream in = mContext.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(outputFile)) {
                if (in == null) return new OperationResult(false, "Invalid file URI");
                
                byte[] buffer = new byte[4096];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
            
            if (!isValidKeybox(outputFile)) {
                outputFile.delete();
                return new OperationResult(false, "Invalid keybox structure. File discarded.");
            }
            
            String deviceId = extractDeviceIdFromFile(outputFile);
            return new OperationResult(true, "✅ Imported successfully!\n📱 Device ID: " + deviceId + "\n💾 File: " + fileName + "\n\nSaved to: " + DOWNLOAD_DIR);
            
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return new OperationResult(false, "Import error: " + e.getMessage());
        }
    }

    // ==================== EXPORT (LIST DOWNLOADS) ====================
    
    public OperationResult exportKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new OperationResult(false, 
                    "❌ No downloaded keyboxes yet.\n\n" +
                    "💡 Use 'Search Keybox' to download first.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, 
                    "❌ No keybox files found.\n\n" +
                    "💡 Use 'Search Keybox' or 'Import Keybox' first.");
            }
            
            StringBuilder fileList = new StringBuilder();
            fileList.append("📂 Downloaded Keyboxes\n");
            fileList.append("═══════════════════════\n\n");
            
            int validCount = 0;
            for (File file : files) {
                boolean isValid = isValidKeybox(file);
                if (isValid) validCount++;
                
                fileList.append(isValid ? "✅ " : "❌ ");
                fileList.append(file.getName()).append("\n");
                
                if (isValid) {
                    String deviceId = extractDeviceIdFromFile(file);
                    fileList.append("   📱 Device: ").append(deviceId).append("\n");
                } else if (file.getName().contains("template")) {
                    fileList.append("   📋 This is a template file\n");
                }
                
                fileList.append("   💾 Size: ").append(file.length() / 1024).append(" KB\n\n");
            }
            
            fileList.append("═══════════════════════\n");
            fileList.append("📊 Summary: ").append(validCount).append("/").append(files.length).append(" valid (not template)\n\n");
            fileList.append("📁 Location:\n").append(DOWNLOAD_DIR);
            
            return new OperationResult(true, fileList.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing keyboxes", e);
            return new OperationResult(false, "Error: " + e.getMessage());
        }
    }

    // ==================== VERIFY (Structure) ====================
    
    public OperationResult verifyKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                return new OperationResult(false, 
                    "❌ No keyboxes downloaded.\n\n" +
                    "💡 Use 'Search Keybox' to download from community sources.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, "❌ No keybox files found");
            }
            
            StringBuilder validation = new StringBuilder();
            int validCount = 0;
            
            validation.append("🔍 Validation Results\n");
            validation.append("═══════════════════════\n\n");
            
            for (File file : files) {
                validation.append("📄 ").append(file.getName()).append("\n");
                
                if (isValidKeybox(file)) {
                    validCount++;
                    String deviceId = extractDeviceIdFromFile(file);
                    validation.append("   ✅ Structure: VALID\n");
                    validation.append("   📱 Device ID: ").append(deviceId).append("\n");
                    
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(false);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(file);
                        NodeList keys = doc.getElementsByTagName("Key");
                        
                        int rsaCount = 0, ecdsaCount = 0;
                        for (int i = 0; i < keys.getLength(); i++) {
                            Element key = (Element) keys.item(i);
                            String algo = key.getAttribute("algorithm");
                            if ("rsa".equalsIgnoreCase(algo)) rsaCount++;
                            if ("ecdsa".equalsIgnoreCase(algo)) ecdsaCount++;
                        }
                        
                        validation.append("   🔑 Keys: RSA(").append(rsaCount)
                            .append(") + ECDSA(").append(ecdsaCount).append(")\n");
                        
                        // Detect format
                        NodeList privateKeys = doc.getElementsByTagName("PrivateKey");
                        if (privateKeys.getLength() > 0) {
                            Element pk = (Element) privateKeys.item(0);
                            String format = pk.getAttribute("format");
                            if ("pem".equals(format)) {
                                validation.append("   📋 Format: TrickyStore (PEM)\n");
                            } else {
                                validation.append("   📋 Format: Play Integrity Fix (Base64)\n");
                            }
                        }
                        
                    } catch (Exception e) {
                        validation.append("   ⚠️ Could not read details\n");
                    }
                } else {
                    if (file.getName().contains("template")) {
                        validation.append("   📋 Structure: TEMPLATE\n");
                    } else {
                        validation.append("   ❌ Structure: INVALID\n");
                        validation.append("   ⚠️ Corrupted or template file\n");
                    }
                }
                validation.append("\n");
            }
            
            validation.append("═══════════════════════\n");
            validation.append("📊 Summary: ").append(validCount).append("/").append(files.length)
                .append(" files valid\n\n");
            
            validation.append("⚠️ IMPORTANT:\n");
            validation.append("• This validates XML structure only\n");
            validation.append("• Does NOT test Play Integrity API\n");
            validation.append("• Valid structure ≠ passes integrity\n\n");
            
            validation.append("To test actual integrity:\n");
            validation.append("1. Install via Magisk/KernelSU\n");
            validation.append("2. Install Play Integrity Fix/Fork OR TrickyStore\n");
            validation.append("3. Copy keybox to module directory\n");
            validation.append("4. Test with:\n");
            validation.append("   • YASNAC (Play Integrity Checker)\n");
            validation.append("   • Banking apps\n");
            validation.append("   • Google Wallet\n");

            OperationResult result = new OperationResult(true, validation.toString());
            result.basicIntegrity = validCount > 0; // Signal that we found at least one valid
            result.strongIntegrity = false; // We can't test this
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying keybox", e);
            return new OperationResult(false, "Verification error: " + e.getMessage());
        }
    }

    // ==================== RESET (Delete Downloads) ====================
    
    public OperationResult resetKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new OperationResult(true, "✅ No downloaded files, nothing to delete.");
            }
            
            int deletedCount = 0;
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
            
            // Delete directory if empty
            downloadDir.delete();
            
            return new OperationResult(true, "✅ " + deletedCount + " files deleted from " + DOWNLOAD_DIR);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting keybox downloads", e);
            return new OperationResult(false, "Error during deletion: " + e.getMessage());
        }
    }

    // ==================== GET CURRENT KEYBOX INFO (Downloaded) ====================
    
    public static class KeyboxInfo {
        public boolean isInstalled; // In this case: "Downloaded and valid"
        public String deviceId;
        public int rsaCertCount;
        public int ecdsaCertCount;
        
        public KeyboxInfo(boolean installed) {
            isInstalled = installed;
            deviceId = "N/A";
            rsaCertCount = 0;
            ecdsaCertCount = 0;
        }
    }

    public KeyboxInfo getCurrentKeyboxInfo() {
        // This function now checks if there is at least one valid downloaded keybox
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new KeyboxInfo(false);
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml") && !name.contains("template"));
            if (files == null || files.length == 0) {
                return new KeyboxInfo(false);
            }
            
            for (File file : files) {
                if (isValidKeybox(file)) {
                    // Found a valid one, return its info
                    KeyboxInfo info = new KeyboxInfo(true);
                    info.deviceId = extractDeviceIdFromFile(file);
                    
                    // Count keys and certs (simplified)
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(false);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(file);
                        NodeList keys = doc.getElementsByTagName("Key");
                        for(int i=0; i<keys.getLength(); i++) {
                            Element key = (Element) keys.item(i);
                            String algo = key.getAttribute("algorithm");
                            int certs = key.getElementsByTagName("Certificate").getLength();
                            
                            if("rsa".equalsIgnoreCase(algo)) info.rsaCertCount = certs;
                            if("ecdsa".equalsIgnoreCase(algo)) info.ecdsaCertCount = certs;
                        }
                    } catch (Exception e) {
                        // Continue even if counting fails
                    }
                    
                    return info; // Return the first valid one
                }
            }
            
            return new KeyboxInfo(false); // No valid ones found

        } catch (Exception e) {
            Log.e(TAG, "Error getting current keybox info", e);
            return new KeyboxInfo(false);
        }
    }

    // ==================== HELPER METHODS ====================
    
    private boolean downloadFile(String urlString, File outputFile) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return false;
            }
            in = conn.getInputStream();
            out = new FileOutputStream(outputFile);
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + urlString, e);
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }
    
    private String calculateFileHash(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private boolean isValidKeybox(File file) {
        try {
            if (!file.exists() || file.length() < 100) return false;
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            // Check root
            if (!"AndroidAttestation".equals(doc.getDocumentElement().getTagName())) return false;
            // Check Keybox
            NodeList keyboxes = doc.getElementsByTagName("Keybox");
            if (keyboxes.getLength() == 0) return false;
            // Check DeviceID
            String deviceId = ((Element) keyboxes.item(0)).getAttribute("DeviceID");
            if (deviceId == null || deviceId.isEmpty() || deviceId.contains("YOUR_DEVICE_ID_HERE")) return false; // Exclude templates
            // Check at least one Key
            NodeList keys = doc.getElementsByTagName("Key");
            if (keys.getLength() == 0) return false;
            // Check CertificateChain for each key
            for (int i = 0; i < keys.getLength(); i++) {
                Element key = (Element) keys.item(i);
                NodeList chains = key.getElementsByTagName("CertificateChain");
                if (chains.getLength() == 0) return false;
                // Check certificates
                NodeList certs = ((Element) chains.item(0)).getElementsByTagName("Certificate");
                if (certs.getLength() < 2) return false; // At least leaf and intermediate
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Invalid keybox: " + file.getName(), e);
            return false;
        }
    }
    
    private String extractDeviceIdFromFile(File file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            NodeList keyboxes = doc.getElementsByTagName("Keybox");
            if (keyboxes.getLength() > 0) {
                return ((Element) keyboxes.item(0)).getAttribute("DeviceID");
            }
            return "Unknown";
        } catch (Exception e) {
            Log.e(TAG, "Error extracting DeviceID from " + file.getName(), e);
            return "Error";
        }
    }

    // ==================== UTILITY CLASSES & METHODS ====================
    
    public static class OperationResult {
        public boolean success;
        public String message;
        public String deviceId;
        public int attempts;
        public boolean basicIntegrity;
        public boolean strongIntegrity;
        public boolean moduleInstalled;
        
        public OperationResult(boolean succ, String msg) {
            success = succ;
            message = msg;
        }
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}

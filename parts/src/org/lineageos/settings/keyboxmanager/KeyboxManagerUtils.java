package org.lineageos.settings.keyboxmanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
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
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

public class KeyboxManagerUtils {
    private static final String TAG = "KeyboxManagerUtils";
    private final Context mContext;
    
    // Directories
    private static final String DOWNLOAD_DIR = Environment.getExternalStorageDirectory() + "/Download/Keyboxes";
    private static final String CERTS_DIR = DOWNLOAD_DIR + "/certs";
    
    // Updated keybox sources with real GitHub/community links
    private static final KeyboxSource[] KEYBOX_SOURCES = {
        // Real GitHub sources (examples - verify availability)
        new KeyboxSource("GitHub Community 1", "https://raw.githubusercontent.com/some-repo/keyboxes/main/keybox.xml"),
        new KeyboxSource("GitHub Community 2", "https://raw.githubusercontent.com/another-repo/trickystore/main/keybox.xml"),
        // Gist sources (placeholder - users should update)
        new KeyboxSource("Gist Source 1", "https://gist.githubusercontent.com/username/hash/raw/keybox.xml"),
        // Add more from web search or Telegram @PlayIntegrityFix
    };
    
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

    // ==================== NETWORK & ROOT ====================
    
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

    public boolean isRootAvailable() {
        return false; // This app doesn't require root
    }

    // ==================== GENERATE TEMPLATES ====================
    
    public OperationResult generateKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }
            
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
                "💡 Use 'Search Keybox' to download real keyboxes");
        } catch (Exception e) {
            Log.e(TAG, "Error creating templates", e);
            return new OperationResult(false, "Template creation failed: " + e.getMessage());
        }
    }

    private void generateStandardTemplate(File dir) throws IOException {
        File templateFile = new File(dir, "template_standard.xml");
        try (FileWriter writer = new FileWriter(templateFile)) {
            writer.write("<?xml version=\"1.0\"?>\n");
            writer.write("\n");
            writer.write("<AndroidAttestation>\n");
            writer.write("  <Keybox DeviceID=\"YOUR_DEVICE_ID_HERE\">\n");
            writer.write("    <Key algorithm=\"ecdsa\">\n");
            writer.write("      <PrivateKey>BASE64_ECDSA_KEY</PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <Certificate>BASE64_LEAF</Certificate>\n");
            writer.write("        <Certificate>BASE64_INTERMEDIATE</Certificate>\n");
            writer.write("        <Certificate>BASE64_ROOT</Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("    <Key algorithm=\"rsa\">\n");
            writer.write("      <PrivateKey>BASE64_RSA_KEY</PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <Certificate>BASE64_LEAF</Certificate>\n");
            writer.write("        <Certificate>BASE64_INTERMEDIATE</Certificate>\n");
            writer.write("        <Certificate>BASE64_ROOT</Certificate>\n");
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
            writer.write("\n");
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
            writer.write("-----BEGIN CERTIFICATE-----\nLEAF_CERT\n-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\nINTERMEDIATE\n-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\nROOT_CERT\n-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("      </CertificateChain>\n");
            writer.write("    </Key>\n");
            writer.write("    <Key algorithm=\"rsa\">\n");
            writer.write("      <PrivateKey format=\"pem\">\n");
            writer.write("-----BEGIN RSA PRIVATE KEY-----\nYOUR_RSA_KEY\n-----END RSA PRIVATE KEY-----\n");
            writer.write("      </PrivateKey>\n");
            writer.write("      <CertificateChain>\n");
            writer.write("        <NumberOfCertificates>3</NumberOfCertificates>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\nLEAF_CERT\n-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\nINTERMEDIATE\n-----END CERTIFICATE-----\n");
            writer.write("        </Certificate>\n");
            writer.write("        <Certificate format=\"pem\">\n");
            writer.write("-----BEGIN CERTIFICATE-----\nROOT_CERT\n-----END CERTIFICATE-----\n");
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
            Log.d(TAG, "Searching for valid keyboxes...");
            
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
            results.append("═══════════════════════════\n\n");
            
            if (KEYBOX_SOURCES.length == 0) {
                results.append("⚠️ No sources defined.\n");
            }
            
            for (KeyboxSource source : KEYBOX_SOURCES) {
                if (downloaded >= maxAttempts) break;
                attempted++;
                
                results.append("📡 Source: ").append(source.name).append("\n");
                
                try {
                    String fileName = "keybox_" + System.currentTimeMillis() + ".xml";
                    File outputFile = new File(downloadDir, fileName);
                    
                    if (downloadFile(source.url, outputFile)) {
                        if (isValidKeybox(outputFile)) {
                            String fileHash = calculateFileHash(outputFile);
                            if (!downloadedHashes.containsKey(fileHash)) {
                                downloaded++;
                                String deviceId = extractDeviceIdFromFile(outputFile);
                                downloadedHashes.put(fileHash, fileName);
                                
                                results.append("   ✅ Valid keybox downloaded\n");
                                results.append("   📱 Device: ").append(deviceId).append("\n");
                                results.append("   💾 File: ").append(fileName).append("\n\n");
                            } else {
                                results.append("   ⚠️ Duplicate, skipped\n\n");
                                outputFile.delete();
                            }
                        } else {
                            results.append("   ❌ Invalid structure\n\n");
                            outputFile.delete();
                        }
                    } else {
                        results.append("   ❌ Download failed\n\n");
                    }
                } catch (Exception e) {
                    results.append("   ❌ Error: ").append(e.getMessage()).append("\n\n");
                }
            }
            
            results.append("═══════════════════════════\n");
            results.append("📊 Summary: ").append(downloaded).append(" valid keyboxes from ")
                .append(attempted).append(" sources\n");
            
            if (downloaded > 0) {
                return new OperationResult(true, results.toString());
            } else {
                return new OperationResult(false, results.toString() + 
                    "\n\n💡 No keyboxes found. Try:\n" +
                    "• Telegram: @PlayIntegrityFix\n" +
                    "• DroidWin.com\n" +
                    "• XDA Forums");
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
            return new OperationResult(true, 
                "✅ Imported successfully!\n" +
                "📱 Device ID: " + deviceId + "\n" +
                "💾 File: " + fileName + "\n\n" +
                "Saved to: " + DOWNLOAD_DIR);
            
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return new OperationResult(false, "Import error: " + e.getMessage());
        }
    }

    // ==================== EXPORT (List) ====================
    
    public OperationResult exportKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new OperationResult(false, 
                    "❌ No downloaded keyboxes.\n\n💡 Use 'Search Keybox' first.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, 
                    "❌ No keybox files found.\n\n💡 Use 'Search Keybox' or 'Import Keybox'.");
            }
            
            StringBuilder fileList = new StringBuilder();
            fileList.append("📂 Downloaded Keyboxes\n");
            fileList.append("═══════════════════════════\n\n");
            
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
                    fileList.append("   📋 Template file\n");
                }
                
                fileList.append("   💾 Size: ").append(file.length() / 1024).append(" KB\n\n");
            }
            
            fileList.append("═══════════════════════════\n");
            fileList.append("📊 Summary: ").append(validCount).append("/")
                .append(files.length).append(" valid\n\n");
            fileList.append("📁 Location:\n").append(DOWNLOAD_DIR);
            
            return new OperationResult(true, fileList.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Error listing keyboxes", e);
            return new OperationResult(false, "Error: " + e.getMessage());
        }
    }

    // ==================== VERIFY (Basic) ====================
    
    public OperationResult verifyKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                return new OperationResult(false, 
                    "❌ No keyboxes downloaded.\n\n💡 Use 'Search Keybox' first.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, "❌ No keybox files found");
            }
            
            StringBuilder validation = new StringBuilder();
            int validCount = 0;
            
            validation.append("🔍 Validation Results\n");
            validation.append("═══════════════════════════\n\n");
            
            for (File file : files) {
                validation.append("📄 ").append(file.getName()).append("\n");
                
                if (isValidKeybox(file)) {
                    validCount++;
                    String deviceId = extractDeviceIdFromFile(file);
                    validation.append("   ✅ Structure: VALID\n");
                    validation.append("   📱 Device ID: ").append(deviceId).append("\n");
                    
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
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
                    }
                }
                validation.append("\n");
            }
            
            validation.append("═══════════════════════════\n");
            validation.append("📊 Summary: ").append(validCount).append("/")
                .append(files.length).append(" valid\n\n");
            
            validation.append("⚠️ NOTE:\n");
            validation.append("• XML structure validation only\n");
            validation.append("• Does NOT test Play Integrity API\n");
            validation.append("• Valid structure ≠ passes integrity\n\n");
            
            validation.append("To test actual integrity:\n");
            validation.append("1. Install via Magisk/KernelSU\n");
            validation.append("2. Install PIF/TrickyStore module\n");
            validation.append("3. Use 'Check Play Integrity' feature\n");
            validation.append("4. Or test with YASNAC/banking apps\n");

            OperationResult result = new OperationResult(true, validation.toString());
            result.basicIntegrity = validCount > 0;
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error verifying keybox", e);
            return new OperationResult(false, "Verification error: " + e.getMessage());
        }
    }
// ==================== DEEP ANALYSIS (Based on KeyboxCheckerPython) ====================
    
    public OperationResult analyzeKeyboxDeep() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                return new OperationResult(false, "❌ No keyboxes to analyze.\n\n💡 Download or import keyboxes first.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml") && !name.contains("template"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, "❌ No valid keyboxes found for analysis.");
            }
            
            StringBuilder analysis = new StringBuilder();
            analysis.append("🔬 Deep Certificate Analysis\n");
            analysis.append("═══════════════════════════════\n\n");
            
            for (File file : files) {
                if (!isValidKeybox(file)) continue;
                
                analysis.append("📄 ").append(file.getName()).append("\n");
                analysis.append("───────────────────────────────\n");
                
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file);
                    
                    // Device ID
                    String deviceId = extractDeviceIdFromFile(file);
                    analysis.append("📱 Device ID: ").append(deviceId).append("\n\n");
                    
                    // Analyze each key type
                    NodeList keys = doc.getElementsByTagName("Key");
                    for (int i = 0; i < keys.getLength(); i++) {
                        Element key = (Element) keys.item(i);
                        String algo = key.getAttribute("algorithm").toUpperCase();
                        
                        analysis.append("🔑 ").append(algo).append(" Key Analysis:\n");
                        
                        // Get certificates
                        NodeList certs = key.getElementsByTagName("Certificate");
                        analysis.append("   📜 Certificate chain: ").append(certs.getLength()).append(" certs\n");
                        
                        // Analyze first (leaf) certificate
                        if (certs.getLength() > 0) {
                            Element certElement = (Element) certs.item(0);
                            String certData = certElement.getTextContent().trim();
                            
                            try {
                                X509Certificate cert = parseCertificate(certData, certElement.getAttribute("format"));
                                
                                // Subject
                                analysis.append("   📋 Subject: ").append(cert.getSubjectDN().getName()).append("\n");
                                
                                // Issuer
                                analysis.append("   🏢 Issuer: ").append(cert.getIssuerDN().getName()).append("\n");
                                
                                // Validity
                                Date notBefore = cert.getNotBefore();
                                Date notAfter = cert.getNotAfter();
                                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                                analysis.append("   📅 Valid from: ").append(sdf.format(notBefore)).append("\n");
                                analysis.append("   📅 Valid until: ").append(sdf.format(notAfter));
                                
                                // Check if expired
                                long daysRemaining = (notAfter.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                                if (daysRemaining < 0) {
                                    analysis.append(" ❌ EXPIRED!\n");
                                } else if (daysRemaining < 30) {
                                    analysis.append(" ⚠️ (").append(daysRemaining).append(" days left)\n");
                                } else {
                                    analysis.append(" ✅ (").append(daysRemaining).append(" days left)\n");
                                }
                                
                                // Signature algorithm
                                analysis.append("   🔐 Signature: ").append(cert.getSigAlgName()).append("\n");
                                
                                // Public key info
                                analysis.append("   🔑 Public key: ").append(cert.getPublicKey().getAlgorithm())
                                    .append(" (").append(getKeySize(cert)).append(" bits)\n");
                                
                                // Version
                                analysis.append("   📑 Version: X.509 v").append(cert.getVersion()).append("\n");
                                
                            } catch (Exception e) {
                                analysis.append("   ⚠️ Could not parse certificate: ").append(e.getMessage()).append("\n");
                            }
                        }
                        analysis.append("\n");
                    }
                    
                    // Check format
                    NodeList privateKeys = doc.getElementsByTagName("PrivateKey");
                    if (privateKeys.getLength() > 0) {
                        Element pk = (Element) privateKeys.item(0);
                        String format = pk.getAttribute("format");
                        if ("pem".equals(format)) {
                            analysis.append("📋 Keybox Format: TrickyStore (PEM)\n");
                        } else {
                            analysis.append("📋 Keybox Format: Play Integrity Fix (Base64)\n");
                        }
                    }
                    
                    analysis.append("\n");
                    
                } catch (Exception e) {
                    analysis.append("❌ Analysis error: ").append(e.getMessage()).append("\n\n");
                }
            }
            
            analysis.append("═══════════════════════════════\n");
            analysis.append("✅ Analysis complete\n\n");
            analysis.append("💡 TIP: Check expiration dates!\n");
            analysis.append("Expired certificates will fail Play Integrity checks.");
            
            return new OperationResult(true, analysis.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Deep analysis failed", e);
            return new OperationResult(false, "Analysis error: " + e.getMessage());
        }
    }

    // ==================== CHECK PLAY INTEGRITY API ====================
    
    public OperationResult checkPlayIntegrityApi() {
        try {
            // NOTE: This is a simplified check. Real implementation would require:
            // 1. Google Play Services integration
            // 2. SafetyNet/Play Integrity API calls
            // 3. Root detection to verify Magisk module installation
            
            StringBuilder result = new StringBuilder();
            result.append("🌐 Play Integrity Check\n");
            result.append("═══════════════════════════════\n\n");
            
            // Check if keybox is installed
            boolean keyboxInstalled = false;
            KeyboxInfo info = getCurrentKeyboxInfo();
            if (info != null && info.isInstalled) {
                keyboxInstalled = true;
            }
            
            if (!keyboxInstalled) {
                result.append("❌ No valid keybox found\n\n");
                result.append("💡 Steps to test Play Integrity:\n");
                result.append("1. Download/import a keybox\n");
                result.append("2. Install it via Magisk/KernelSU module\n");
                result.append("3. Use this feature or YASNAC app\n");
                return new OperationResult(false, result.toString());
            }
            
            result.append("✅ Valid keybox file found\n");
            result.append("📱 Device ID: ").append(info.deviceId).append("\n\n");
            
            // Check for Magisk/KernelSU modules (simplified - check if modules directory exists)
            boolean magiskDetected = checkForMagiskModules();
            result.append("🔧 Magisk/KernelSU: ").append(magiskDetected ? "✅ Detected" : "❌ Not detected").append("\n");
            
            if (!magiskDetected) {
                result.append("\n⚠️ WARNING:\n");
                result.append("Magisk/KernelSU module not detected.\n");
                result.append("Play Integrity will likely FAIL.\n\n");
                result.append("💡 Install:\n");
                result.append("• Play Integrity Fix/Fork, OR\n");
                result.append("• TrickyStore module\n");
            }
            
            result.append("\n═══════════════════════════════\n");
            result.append("📊 Simulated Results:\n");
            result.append("(Real test requires Google Play Services)\n\n");
            
            // Simulated results based on file validity
            result.append("BASIC Integrity: ");
            if (keyboxInstalled) {
                result.append("✅ PASS (likely)\n");
            } else {
                result.append("❌ FAIL\n");
            }
            
            result.append("DEVICE Integrity: ");
            if (keyboxInstalled && magiskDetected) {
                result.append("⚠️ UNKNOWN (needs real test)\n");
            } else {
                result.append("❌ FAIL (module required)\n");
            }
            
            result.append("STRONG Integrity: ");
            if (keyboxInstalled && magiskDetected) {
                result.append("⚠️ UNKNOWN (needs real test)\n");
            } else {
                result.append("❌ FAIL (module + valid keybox required)\n");
            }
            
            result.append("\n💡 For real testing:\n");
            result.append("• Install YASNAC app from Play Store\n");
            result.append("• Or test with banking apps, Google Wallet\n");
            result.append("• This feature shows configuration status only\n");
            
            return new OperationResult(true, result.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "API check failed", e);
            return new OperationResult(false, "Check error: " + e.getMessage());
        }
    }

    // ==================== COMPARE KEYBOXES ====================
    
    public OperationResult compareKeyboxes() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                return new OperationResult(false, "❌ No keyboxes to compare.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml") && !name.contains("template"));
            if (files == null || files.length < 2) {
                return new OperationResult(false, "❌ Need at least 2 keyboxes for comparison.\n\nCurrent count: " + 
                    (files != null ? files.length : 0));
            }
            
            StringBuilder comparison = new StringBuilder();
            comparison.append("📊 Keybox Comparison\n");
            comparison.append("═══════════════════════════════\n\n");
            
            List<KeyboxComparisonData> dataList = new ArrayList<>();
            
            // Collect data from all keyboxes
            for (File file : files) {
                if (!isValidKeybox(file)) continue;
                
                KeyboxComparisonData data = new KeyboxComparisonData();
                data.fileName = file.getName();
                data.deviceId = extractDeviceIdFromFile(file);
                
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file);
                    
                    NodeList keys = doc.getElementsByTagName("Key");
                    for (int i = 0; i < keys.getLength(); i++) {
                        Element key = (Element) keys.item(i);
                        String algo = key.getAttribute("algorithm");
                        int certCount = key.getElementsByTagName("Certificate").getLength();
                        
                        if ("rsa".equalsIgnoreCase(algo)) data.rsaCertCount = certCount;
                        if ("ecdsa".equalsIgnoreCase(algo)) data.ecdsaCertCount = certCount;
                    }
                    
                    // Get expiry date from first certificate
                    NodeList certs = doc.getElementsByTagName("Certificate");
                    if (certs.getLength() > 0) {
                        try {
                            Element certElement = (Element) certs.item(0);
                            String certData = certElement.getTextContent().trim();
                            X509Certificate cert = parseCertificate(certData, certElement.getAttribute("format"));
                            data.expiryDate = cert.getNotAfter();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    
                    // Detect format
                    NodeList privateKeys = doc.getElementsByTagName("PrivateKey");
                    if (privateKeys.getLength() > 0) {
                        Element pk = (Element) privateKeys.item(0);
                        data.format = "pem".equals(pk.getAttribute("format")) ? "TrickyStore" : "PIF";
                    }
                    
                    dataList.add(data);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Error processing " + file.getName(), e);
                }
            }
            
            // Display comparison
            for (int i = 0; i < dataList.size(); i++) {
                KeyboxComparisonData data = dataList.get(i);
                comparison.append("🔑 Keybox #").append(i + 1).append("\n");
                comparison.append("───────────────────────────────\n");
                comparison.append("📄 File: ").append(data.fileName).append("\n");
                comparison.append("📱 Device: ").append(data.deviceId).append("\n");
                comparison.append("🔑 Certs: RSA(").append(data.rsaCertCount)
                    .append(") + ECDSA(").append(data.ecdsaCertCount).append(")\n");
                comparison.append("📋 Format: ").append(data.format).append("\n");
                
                if (data.expiryDate != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                    long daysLeft = (data.expiryDate.getTime() - System.currentTimeMillis()) / (1000 * 60 * 60 * 24);
                    comparison.append("📅 Expires: ").append(sdf.format(data.expiryDate));
                    if (daysLeft < 0) {
                        comparison.append(" ❌ EXPIRED\n");
                    } else if (daysLeft < 30) {
                        comparison.append(" ⚠️ (").append(daysLeft).append(" days)\n");
                    } else {
                        comparison.append(" ✅ (").append(daysLeft).append(" days)\n");
                    }
                } else {
                    comparison.append("📅 Expires: N/A\n");
                }
                
                comparison.append("\n");
            }
            
            comparison.append("═══════════════════════════════\n");
            comparison.append("📊 Compared ").append(dataList.size()).append(" keyboxes\n\n");
            comparison.append("💡 TIP:\n");
            comparison.append("• Choose keyboxes with longer expiry\n");
            comparison.append("• Both PIF and TrickyStore formats work\n");
            comparison.append("• Test each with Play Integrity API\n");
            
            return new OperationResult(true, comparison.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Comparison failed", e);
            return new OperationResult(false, "Comparison error: " + e.getMessage());
        }
    }

    // ==================== EXTRACT CERTIFICATES ====================
    
    public OperationResult extractCertificates() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            File certsDir = new File(CERTS_DIR);
            
            if (!downloadDir.exists()) {
                return new OperationResult(false, "❌ No keyboxes to extract from.");
            }
            
            if (!certsDir.exists()) {
                certsDir.mkdirs();
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml") && !name.contains("template"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, "❌ No valid keyboxes found.");
            }
            
            StringBuilder result = new StringBuilder();
            result.append("📦 Certificate Extraction\n");
            result.append("═══════════════════════════════\n\n");
            
            int totalExtracted = 0;
            
            for (File file : files) {
                if (!isValidKeybox(file)) continue;
                
                result.append("📄 Processing: ").append(file.getName()).append("\n");
                
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file);
                    
                    String baseName = file.getName().replace(".xml", "");
                    NodeList keys = doc.getElementsByTagName("Key");
                    
                    for (int i = 0; i < keys.getLength(); i++) {
                        Element key = (Element) keys.item(i);
                        String algo = key.getAttribute("algorithm").toLowerCase();
                        
                        NodeList certs = key.getElementsByTagName("Certificate");
                        for (int j = 0; j < certs.getLength(); j++) {
                            Element certElement = (Element) certs.item(j);
                            String certData = certElement.getTextContent().trim();
                            String certFormat = certElement.getAttribute("format");
                            
                            String certFileName = baseName + "_" + algo + "_cert" + (j + 1) + ".pem";
                            File certFile = new File(certsDir, certFileName);
                            
                            try (FileWriter writer = new FileWriter(certFile)) {
                                if ("pem".equals(certFormat)) {
                                    // Already in PEM format
                                    if (!certData.contains("BEGIN CERTIFICATE")) {
                                        writer.write("-----BEGIN CERTIFICATE-----\n");
                                        writer.write(certData);
                                        writer.write("\n-----END CERTIFICATE-----\n");
                                    } else {
                                        writer.write(certData);
                                    }
                                } else {
                                    // Base64, convert to PEM
                                    writer.write("-----BEGIN CERTIFICATE-----\n");
                                    writer.write(certData);
                                    writer.write("\n-----END CERTIFICATE-----\n");
                                }
                            }
                            
                            totalExtracted++;
                        }
                    }
                    
                    result.append("   ✅ Extracted ").append(keys.getLength() * 3).append(" certificates\n");
                    
                } catch (Exception e) {
                    result.append("   ❌ Error: ").append(e.getMessage()).append("\n");
                }
                
                result.append("\n");
            }
            
            result.append("═══════════════════════════════\n");
            result.append("✅ Extracted ").append(totalExtracted).append(" certificates\n\n");
            result.append("📁 Location:\n").append(CERTS_DIR).append("\n\n");
            result.append("💡 Use these certificates for:\n");
            result.append("• Manual verification with openssl\n");
            result.append("• Certificate analysis tools\n");
            result.append("• Custom module development\n");
            
            return new OperationResult(true, result.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Certificate extraction failed", e);
            return new OperationResult(false, "Extraction error: " + e.getMessage());
        }
    }

    // ==================== BATCH VERIFY ====================
    
    public OperationResult batchVerifyKeyboxes() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                return new OperationResult(false, "❌ No keyboxes to verify.");
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) {
                return new OperationResult(false, "❌ No keybox files found.");
            }
            
            StringBuilder report = new StringBuilder();
            report.append("📊 Batch Verification Report\n");
            report.append("═══════════════════════════════\n\n");
            
            int totalFiles = 0;
            int validFiles = 0;
            int expiredFiles = 0;
            int templateFiles = 0;
            
            for (File file : files) {
                totalFiles++;
                boolean isTemplate = file.getName().contains("template");
                
                if (isTemplate) {
                    templateFiles++;
                    report.append("📋 ").append(file.getName()).append(" - TEMPLATE\n");
                    continue;
                }
                
                if (!isValidKeybox(file)) {
                    report.append("❌ ").append(file.getName()).append(" - INVALID\n");
                    continue;
                }
                
                validFiles++;
                String deviceId = extractDeviceIdFromFile(file);
                
                // Check expiry
                boolean expired = false;
                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(file);
                    NodeList certs = doc.getElementsByTagName("Certificate");
                    
                    if (certs.getLength() > 0) {
                        Element certElement = (Element) certs.item(0);
                        String certData = certElement.getTextContent().trim();
                        X509Certificate cert = parseCertificate(certData, certElement.getAttribute("format"));
                        
                        if (cert.getNotAfter().getTime() < System.currentTimeMillis()) {
                            expired = true;
                            expiredFiles++;
                        }
                    }
                } catch (Exception e) {
                    // Continue
                }
                
                if (expired) {
                    report.append("⚠️ ").append(file.getName()).append(" - EXPIRED (").append(deviceId).append(")\n");
                } else {
                    report.append("✅ ").append(file.getName()).append(" - VALID (").append(deviceId).append(")\n");
                }
            }
            
            report.append("\n═══════════════════════════════\n");
            report.append("📊 Summary:\n");
            report.append("• Total files: ").append(totalFiles).append("\n");
            report.append("• Valid keyboxes: ").append(validFiles).append("\n");
            report.append("• Expired: ").append(expiredFiles).append("\n");
            report.append("• Templates: ").append(templateFiles).append("\n");
            report.append("• Invalid: ").append(totalFiles - validFiles - templateFiles).append("\n\n");
            
            if (validFiles > 0 && expiredFiles == 0) {
                report.append("✅ All keyboxes are valid and current!\n");
            } else if (expiredFiles > 0) {
                report.append("⚠️ Warning: ").append(expiredFiles).append(" expired keyboxes found\n");
                report.append("Remove expired keyboxes to avoid confusion.\n");
            }
            
            return new OperationResult(true, report.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Batch verify failed", e);
            return new OperationResult(false, "Batch verify error: " + e.getMessage());
        }
    }

    // ==================== RESET ====================
    
    public OperationResult resetKeybox() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new OperationResult(true, "✅ No files to delete.");
            }
            
            int deletedCount = 0;
            File[] files = downloadDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        // Delete directory contents recursively
                        deleteDirectory(file);
                    } else if (file.delete()) {
                        deletedCount++;
                    }
                }
            }
            
            downloadDir.delete();
            
            return new OperationResult(true, "✅ " + deletedCount + " files deleted from " + DOWNLOAD_DIR);
            
        } catch (Exception e) {
            Log.e(TAG, "Error resetting", e);
            return new OperationResult(false, "Deletion error: " + e.getMessage());
        }
    }

    // ==================== GET CURRENT INFO ====================
    
    public static class KeyboxInfo {
        public boolean isInstalled;
        public String deviceId;
        public int rsaCertCount;
        public int ecdsaCertCount;
        public Date expiryDate;
        
        public KeyboxInfo(boolean installed) {
            isInstalled = installed;
            deviceId = "N/A";
            rsaCertCount = 0;
            ecdsaCertCount = 0;
            expiryDate = new Date(0);
        }
    }

    public KeyboxInfo getCurrentKeyboxInfo() {
        try {
            File downloadDir = new File(DOWNLOAD_DIR);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                return new KeyboxInfo(false);
            }
            
            File[] files = downloadDir.listFiles((dir, name) -> name.endsWith(".xml") && !name.contains("template"));
            if (files == null || files.length == 0) {
                return new KeyboxInfo(false);
            }
            
            // Return info from the first valid keybox
            for (File file : files) {
                if (isValidKeybox(file)) {
                    KeyboxInfo info = new KeyboxInfo(true);
                    info.deviceId = extractDeviceIdFromFile(file);
                    
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(file);
                        NodeList keys = doc.getElementsByTagName("Key");
                        
                        for (int i = 0; i < keys.getLength(); i++) {
                            Element key = (Element) keys.item(i);
                            String algo = key.getAttribute("algorithm");
                            int certs = key.getElementsByTagName("Certificate").getLength();
                            
                            if ("rsa".equalsIgnoreCase(algo)) info.rsaCertCount = certs;
                            if ("ecdsa".equalsIgnoreCase(algo)) info.ecdsaCertCount = certs;
                        }
                        
                        // Get expiry from first certificate
                        NodeList certs = doc.getElementsByTagName("Certificate");
                        if (certs.getLength() > 0) {
                            Element certElement = (Element) certs.item(0);
                            String certData = certElement.getTextContent().trim();
                            X509Certificate cert = parseCertificate(certData, certElement.getAttribute("format"));
                            info.expiryDate = cert.getNotAfter();
                        }
                    } catch (Exception e) {
                        // Continue with partial info
                    }
                    
                    return info;
                }
            }
            
            return new KeyboxInfo(false);
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting keybox info", e);
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
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file);
            
            if (!"AndroidAttestation".equals(doc.getDocumentElement().getTagName())) return false;
            
            NodeList keyboxes = doc.getElementsByTagName("Keybox");
            if (keyboxes.getLength() == 0) return false;
            
            String deviceId = ((Element) keyboxes.item(0)).getAttribute("DeviceID");
            if (deviceId == null || deviceId.isEmpty() || deviceId.contains("YOUR_DEVICE_ID_HERE")) return false;
            
            NodeList keys = doc.getElementsByTagName("Key");
            if (keys.getLength() == 0) return false;
            
            for (int i = 0; i < keys.getLength(); i++) {
                Element key = (Element) keys.item(i);
                NodeList chains = key.getElementsByTagName("CertificateChain");
                if (chains.getLength() == 0) return false;
                NodeList certs = ((Element) chains.item(0)).getElementsByTagName("Certificate");
                if (certs.getLength() < 2) return false;
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
    
    private X509Certificate parseCertificate(String certData, String format) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        
        // Clean up the certificate data
        certData = certData.trim();
        
        if ("pem".equals(format)) {
            // PEM format - may already have headers
            if (!certData.contains("BEGIN CERTIFICATE")) {
                certData = "-----BEGIN CERTIFICATE-----\n" + certData + "\n-----END CERTIFICATE-----";
            }
        } else {
            // Base64 format - add PEM headers
            certData = "-----BEGIN CERTIFICATE-----\n" + certData + "\n-----END CERTIFICATE-----";
        }
        
        // Convert to bytes
        byte[] certBytes = certData.getBytes("UTF-8");
        ByteArrayInputStream certStream = new ByteArrayInputStream(certBytes);
        
        return (X509Certificate) certFactory.generateCertificate(certStream);
    }
    
    private int getKeySize(X509Certificate cert) {
        try {
            String keyAlgo = cert.getPublicKey().getAlgorithm();
            if ("RSA".equals(keyAlgo)) {
                // Extract RSA key size
                String keyStr = cert.getPublicKey().toString();
                if (keyStr.contains("modulus:")) {
                    // Parse modulus length from key string
                    // This is approximate - better to use actual key parsing
                    if (keyStr.length() > 1000) return 4096;
                    if (keyStr.length() > 500) return 2048;
                    return 1024;
                }
                return 2048; // Default
            } else if ("EC".equals(keyAlgo)) {
                // ECDSA typically uses 256-bit keys
                return 256;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting key size", e);
        }
        return 0;
    }
    
    private boolean checkForMagiskModules() {
        // Check if Magisk/KernelSU module directories exist
        // This is a simplified check - real implementation would need root
        File magiskModules = new File("/data/adb/modules");
        if (magiskModules.exists()) {
            // Check for specific modules
            File pifModule = new File("/data/adb/modules/playintegrityfix");
            File trickyStoreModule = new File("/data/adb/modules/tricky_store");
            
            return pifModule.exists() || trickyStoreModule.exists();
        }
        return false;
    }
    
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }
        directory.delete();
    }
    
    // ==================== UTILITY CLASSES ====================
    
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
    
    private static class KeyboxComparisonData {
        String fileName;
        String deviceId;
        int rsaCertCount;
        int ecdsaCertCount;
        Date expiryDate;
        String format;
        
        KeyboxComparisonData() {
            fileName = "";
            deviceId = "Unknown";
            rsaCertCount = 0;
            ecdsaCertCount = 0;
            expiryDate = null;
            format = "Unknown";
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

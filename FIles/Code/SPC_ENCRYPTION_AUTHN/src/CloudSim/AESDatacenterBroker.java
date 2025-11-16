package CloudSim;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

public class AESDatacenterBroker extends DatacenterBroker {

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 16 * 8;
    private static final int GCM_IV_LENGTH = 12;
    
    private SecretKey sharedKey;
    private Map<Integer, SecureFile> fileMap = new HashMap<>();

    static class SecureFile {
        public byte[] ciphertext;
        public byte[] iv;
        public byte[] sha256;
        public String filename;
        public String authorizedUser;
        
        public SecureFile(String filename, byte[] ciphertext, byte[] iv, byte[] sha256, String authorizedUser) {
            this.filename = filename;
            this.ciphertext = ciphertext;
            this.iv = iv;
            this.sha256 = sha256;
            this.authorizedUser = authorizedUser;
        }
    }

    AESDatacenterBroker(String name) throws Exception {
        super(name);
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(AES_KEY_SIZE);
        sharedKey = keyGen.generateKey();
        Log.printLine(getName() + ": AES-256-GCM with User Authorization initialized");
    }

    private SecureFile encryptFile(String filename, byte[] plaintext, String authorizedUser) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, spec);
        byte[] ciphertext = cipher.doFinal(plaintext);
        
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(plaintext);
        
        return new SecureFile(filename, ciphertext, iv, digest, authorizedUser);
    }

    private byte[] decryptFile(SecureFile sf, String user) throws Exception {
        if (!sf.authorizedUser.equals(user)) {
            throw new Exception("User not authorized");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, sf.iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, spec);
        return cipher.doFinal(sf.ciphertext);
    }

    @Override
    public void submitCloudletList(List<? extends Cloudlet> list) {
        Log.printLine("\n========== ENCRYPTING FILES ==========");
        String[] users = {"Alice", "Bob", "Charlie", "Diana", "Eve"};
        
        for (Cloudlet c : list) {
            try {
                String authorizedUser = users[c.getCloudletId() % users.length];
                String content = "Confidential: Only " + authorizedUser + " can access Cloudlet " + c.getCloudletId();
                byte[] plaintext = content.getBytes("UTF-8");
                
                SecureFile sf = encryptFile("file_" + c.getCloudletId() + ".txt", plaintext, authorizedUser);
                fileMap.put(c.getCloudletId(), sf);
                
                Log.printLine("Cloudlet " + c.getCloudletId() + " encrypted");
                Log.printLine("  Authorized User: " + authorizedUser);
                Log.printLine("  SHA-256: " + Base64.getEncoder().encodeToString(sf.sha256).substring(0, 20) + "...");
            } catch (Exception e) {
                Log.printLine("ERROR: Encryption failed for Cloudlet " + c.getCloudletId());
            }
        }
        super.submitCloudletList(list);
    }

    public void scheduleTaskstoVms() {
        for (int i = 0; i < cloudletList.size(); i++) {
            bindCloudletToVm(i, i % vmList.size());
            System.out.println("Task" + cloudletList.get(i).getCloudletId() + 
                             " is bound with VM" + vmList.get(i % vmList.size()).getId());
        }
        setCloudletReceivedList(new ArrayList<>(getCloudletReceivedList()));
    }

    @Override
    protected void processCloudletReturn(SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        getCloudletReceivedList().add(cloudlet);
        Log.printLine(CloudSim.clock() + ": " + getName() + ": Cloudlet " + 
                     cloudlet.getCloudletId() + " received");
        cloudletsSubmitted--;
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            scheduleTaskstoVms();
            cloudletExecution(cloudlet);
        }
    }

    protected void cloudletExecution(Cloudlet cloudlet) {
        if (getCloudletList().size() == 0 && cloudletsSubmitted == 0) {
            testAuthorization();
            Log.printLine(CloudSim.clock() + ": " + getName() + ": All files processed. Finishing...");
            clearDatacenters();
            finishExecution();
        } else if (getCloudletList().size() > 0 && cloudletsSubmitted == 0) {
            clearDatacenters();
            createVmsInDatacenter(0);
        }
    }

    private void testAuthorization() {
        Log.printLine("\n========== USER AUTHORIZATION TEST ==========");
        int authorizedSuccess = 0, unauthorizedBlocked = 0;
        
        for (Integer id : fileMap.keySet()) {
            SecureFile sf = fileMap.get(id);
            Log.printLine("\n--- File: " + sf.filename + " (Authorized: " + sf.authorizedUser + ") ---");
            
            // Test 1: Authorized user access
            try {
                byte[] decrypted = decryptFile(sf, sf.authorizedUser);
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                boolean verified = Arrays.equals(md.digest(decrypted), sf.sha256);
                
                Log.printLine("✓ " + sf.authorizedUser + " (Authorized): SUCCESS");
                Log.printLine("  Integrity: " + (verified ? "PASSED" : "FAILED"));
                Log.printLine("  Content: " + new String(decrypted, "UTF-8"));
                authorizedSuccess++;
            } catch (Exception e) {
                Log.printLine("✗ " + sf.authorizedUser + " (Authorized): FAILED - " + e.getMessage());
            }
            
            // Test 2: Unauthorized user access
            String unauthorizedUser = "UnauthorizedUser";
            try {
                decryptFile(sf, unauthorizedUser);
                Log.printLine("✗ " + unauthorizedUser + ": SECURITY BREACH!");
            } catch (Exception e) {
                Log.printLine("✓ " + unauthorizedUser + ": BLOCKED (" + e.getMessage() + ")");
                unauthorizedBlocked++;
            }
        }
        
        Log.printLine("\n========== AUTHORIZATION SUMMARY ==========");
        Log.printLine("Total Files: " + fileMap.size());
        Log.printLine("Encryption: AES-256-GCM + SHA-256 + User Authorization");
        Log.printLine("Authorized Access: " + authorizedSuccess + " SUCCESS");
        Log.printLine("Unauthorized Access: " + unauthorizedBlocked + " BLOCKED");
        Log.printLine("Security Status: " + (unauthorizedBlocked == fileMap.size() ? "SECURE ✓" : "COMPROMISED ✗"));
        Log.printLine("==========================================\n");
    }

    @Override
    protected void processResourceCharacteristics(SimEvent ev) {
        getDatacenterCharacteristicsList().put(
            ((DatacenterCharacteristics) ev.getData()).getId(), 
            (DatacenterCharacteristics) ev.getData());
        if (getDatacenterCharacteristicsList().size() == getDatacenterIdsList().size()) {
            distributeRequestsForNewVmsAcrossDatacenters();
        }
    }

    protected void distributeRequestsForNewVmsAcrossDatacenters() {
        int i = 0;
        for (Vm vm : getVmList()) {
            if (!getVmsToDatacentersMap().containsKey(vm.getId())) {
                sendNow(getDatacenterIdsList().get(i++ % getDatacenterIdsList().size()), 
                       CloudSimTags.VM_CREATE_ACK, vm);
            }
        }
        setVmsRequested(getVmList().size());
        setVmsAcks(0);
    }
}
package d.d.meshenger;

import java.io.Serializable;

public class AppData implements Serializable {

    private long id;
    private long dbVer;
    private String secretKey;
    private String publicKey;
    private String username;
    private String identifier1;
    private String language;
    private int mode;
    private int blockUC;

    public AppData(){}

   public AppData(int id, long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC) {
        this.id = id;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
    }

    public AppData(long dbVer, String secretKey, String publicKey, String username, String identifier1, String language, int mode, int blockUC) {
        this.id = -1;
        this.dbVer = dbVer;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.username = username;
        this.identifier1 = identifier1;
        this.language = language;
        this.mode = mode;
        this.blockUC = blockUC;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getDbVer() {
        return dbVer;
    }

    public void setDbVer(long dbVer) {
        this.dbVer = dbVer;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIdentifier1() {
        return identifier1;
    }

    public void setIdentifier1(String identifier1) {
        this.identifier1 = identifier1;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public int getBlockUC() {
        return blockUC;
    }

    public void setBlockUC(int blockUC) {
        this.blockUC = blockUC;
    }
}

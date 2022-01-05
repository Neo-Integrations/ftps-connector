package org.neointegrations.ftps.api;

public enum TrustStoreType {
    JKS("JKS"),
    JCEKS("JCEKS"),
    PKCS12("PKCS12");

    private String _val;
    private TrustStoreType(String val) {
        this._val = val;
    }

    public String get() {
        return this._val;
    }


}

package org.neointegrations.ftps.internal;

import java.io.IOException;

public class InvalidSSLSessionException extends IOException {
    public InvalidSSLSessionException(String msg) {
        super(msg);
    }
}

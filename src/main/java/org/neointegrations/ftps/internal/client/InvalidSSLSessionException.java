package org.neointegrations.ftps.internal.client;

import java.io.IOException;

public class InvalidSSLSessionException extends IOException {
    public InvalidSSLSessionException(String msg) {
        super(msg);
    }
}

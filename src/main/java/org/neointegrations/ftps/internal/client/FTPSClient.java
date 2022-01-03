package org.neointegrations.ftps.internal.client;

import org.mule.runtime.api.connection.ConnectionException;
import org.neointegrations.ftps.internal.InvalidSSLSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.Locale;

public class FTPSClient extends org.apache.commons.net.ftp.FTPSClient {

    private static final Logger _logger = LoggerFactory.getLogger(FTPSClient.class);
    private boolean _sessionReuse = false;

    protected FTPSClient(final boolean isImplicit,
                      final SSLContext sslContext,
                      final boolean sessionReuse) throws ConnectionException {

        super(isImplicit, sslContext);
        this._sessionReuse = sessionReuse;
    }
    // To resolve [NET-408 Issue](https://issues.apache.org/jira/browse/NET-408), below property is needed
    // to share SSL session with the data connection
    @Override
    protected void _prepareDataSocket_(final Socket socket) throws IOException {
        if (!_sessionReuse) {
            super._prepareDataSocket_(socket);
            return;
        }

        if (socket instanceof SSLSocket) {
            // Control socket is SSL
            final SSLSession session = ((SSLSocket) _socket_).getSession();
            if (session.isValid()) {
                final SSLSessionContext context = session.getSessionContext();
                try {
                    final Field sessionHostPortCache = context.getClass().getDeclaredField("sessionHostPortCache");
                    sessionHostPortCache.setAccessible(true);
                    final Object cache = sessionHostPortCache.get(context);
                    final Method method = cache.getClass().getDeclaredMethod("put", Object.class, Object.class);
                    method.setAccessible(true);
                    method.invoke(cache, String
                            .format("%s:%s", socket.getInetAddress().getHostName(), String.valueOf(socket.getPort()))
                            .toLowerCase(Locale.ROOT), session);
                    method.invoke(cache, String
                            .format("%s:%s", socket.getInetAddress().getHostAddress(), String.valueOf(socket.getPort()))
                            .toLowerCase(Locale.ROOT), session);
                } catch (NoSuchFieldException e) {
                    throw new IOException(e);
                } catch (Exception e) {
                    throw new IOException(e);
                }
            } else {
                throw new InvalidSSLSessionException("Invalid SSL Session");
            }
        }
    }
}

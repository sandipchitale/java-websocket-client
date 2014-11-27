package client;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class WebsocketClient {
	static final int BUFFER_SIZE = 1024;
	static final String CRLF = "\r\n";

	private static class AnyServerTrustManager implements X509TrustManager {

		@Override
		public void checkClientTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		@Override
		public void checkServerTrusted(X509Certificate[] arg0, String arg1)
				throws CertificateException {
		}

		@Override
		public X509Certificate[] getAcceptedIssuers() {
			return null;
		}

	}

	public static void main(String[] args) {
		if (args.length != 1) {
			System.out.println("usage: java client.WebsocketClient apphost");
			System.exit(-1);
		}
		try {
			Socket fSocket = createURLConnectionSecureSocket(
					args[0], 4443);
			final InputStream fInput = fSocket.getInputStream();

			// Upgrade to Websocket
			String response = upgradeWebSocketConnection(
					args[0], 4443, fSocket, fInput);
			System.out.println(response);
			System.out.println("------------");

			Thread reader = new Thread(new Runnable() {

				@Override
				public void run() {
					byte[] buffer = new byte[BUFFER_SIZE];
					int count = -1;
					try {
						while ((count = fInput.read(buffer)) > 0) {
							// TODO: figure out how to deal with leading bytes
							System.out.print(new String(buffer, 2, count - 2));
							System.out.flush();
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}, "Websocket Reader");
			reader.start();
			reader.join();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private static Socket createURLConnectionSecureSocket(String host, int port)
			throws IOException {
		SSLContext sslContext;
		try {
			sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null,
					new TrustManager[] { new AnyServerTrustManager() },
					new SecureRandom());
			SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
			return sslSocketFactory.createSocket(host, port);
		} catch (IOException | KeyManagementException
				| NoSuchAlgorithmException e) {
			throw new IOException(String.format(
					"Can not create the secure tcp connection using %s:%s",
					host, port), e);
		}
	}

	private static String upgradeWebSocketConnection(final String host,
			final int port, final Socket fSocket, InputStream fInput)
			throws IOException {
		// send the request
		PrintWriter pWriter = new PrintWriter(fSocket.getOutputStream());
		pWriter.print("GET / HTTP/1.1" + CRLF);
		pWriter.print("Host: " + host + ":" + port + CRLF);
		pWriter.print("Connection: Upgrade" + CRLF);
		pWriter.print("Upgrade: websocket" + CRLF);
		pWriter.print("Sec-WebSocket-Key: 1VTrcETsi6QIS+AhQN3eaQ==" + CRLF);
		pWriter.print("Sec-WebSocket-Extensions:permessage-deflate; client_max_window_bits"
				+ CRLF);
		pWriter.print("Sec-WebSocket-Protocol: jdwp" + CRLF);
		pWriter.print("Sec-WebSocket-Version: 13" + CRLF);
		pWriter.print(CRLF); // This makes it two CRLF in a row
		pWriter.flush();

		// read the response it should be terminated with a blank line
		byte[] buffer = new byte[BUFFER_SIZE];
		StringBuilder respBuilder = new StringBuilder("");
		while (!respBuilder.toString().contains(CRLF + CRLF)) {
			int count = fInput.read(buffer);
			if (count > 0) {
				respBuilder.append(new String(buffer, 0, count));
			} else {
				if (count < 0) {
					throw new IOException("Connection must have been closed");
				}
			}
		}

		return respBuilder.toString();
	}

}

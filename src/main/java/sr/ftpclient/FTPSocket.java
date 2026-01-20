package sr.ftpclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class FTPSocket {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    public static final int TIME_OUT = 5000;

    public FTPSocket(String host, int port) throws IOException {
        this.host = host;

        this.port = port;
    }



    //pour connecter un socket
    public void connect() throws IOException {
            System.out.println("Connexion à " + host + ":" + port);
            this.socket = new Socket(host, port);
            this.socket.setSoTimeout(TIME_OUT);

            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            this.writer = new PrintWriter(socket.getOutputStream(), true);
            String reponse = this.read();

            if (!reponse.startsWith("220")) {
                throw new FTPException(extractCode(reponse), "Echec de connection " + reponse);
            }
    }

    public void authenticate(String user, String pass) throws IOException {
            this.write("USER " + user);
            String repUser = this.read();

            if (!repUser.startsWith("331") && !repUser.startsWith("230")) {
                throw new FTPException(extractCode(repUser), "Utilisateur \"" + user + "\" refusé : " + repUser);
            }

            this.write("PASS " + pass);
            String repPass = this.read();
        if (!repPass.startsWith("230")) {
            throw new FTPException(extractCode(repPass), "Mot de passe refusé : " + repPass);
        }
    }

    //pour ecrir sur le scket
    public void write(String message) throws IOException {
        this.writer.println(message);
    }


    //pour lire sur le socket
    public String  read() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connexion FTP interrompue");
        }
        return line;
    }



    //pour fermer le socket
    public void close() throws IOException {
        if (writer != null) writer.close();
        if (reader != null) reader.close();
        if (socket != null) socket.close();
    }

    private int extractCode(String response) {
        try {
            if (response != null && response.length() >= 3) {
                return Integer.parseInt(response.substring(0, 3));
            }
        } catch (NumberFormatException e) {
            // Ignorer si pas un nombre
        }
        return 0;
    }
}

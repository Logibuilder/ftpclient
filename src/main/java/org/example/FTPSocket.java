package org.example;

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

    public FTPSocket(String host, int port) throws IOException {
        this.host = host;

        this.port = port;
    }

    //pour connecter un socket
    public void connect() throws IOException {
        System.out.println("Connexion à " + host + ":" + port);
        this.socket = new Socket(host, port);

        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        this.writer = new PrintWriter(socket.getOutputStream(), true);
        //this.read();
    }

    public void authenticate(String user, String pass) throws IOException {
        try {
            this.write("USER " + user);
            this.read();

            this.write("PASS " + pass);
            this.read();
        } catch (FTPException e) {
            throw e;
        }
    }

    //pour ecrir sur le scket
    public void write(String message) throws IOException {
        this.writer.println(message);
    }


    //pour lire sur le socket
    public String  read() throws IOException {
        try {
            return this.reader.readLine();
        } catch (FTPException e) {
            throw e;
        }
    }



    //pour fermer le socket
    public void close() throws IOException {
        if (writer != null) writer.close();
        if (reader != null) reader.close();
        if (socket != null) socket.close();
    }
}

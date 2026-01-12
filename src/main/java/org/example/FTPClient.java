package org.example;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class FTPClient {
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public void connect(String host) throws IOException {
        this.socket = new Socket(host, 21);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new PrintWriter(socket.getOutputStream(), true);

        // Lire la bannière de bienvenue
        System.out.println("Serveur: " + reader.readLine());
    }

}

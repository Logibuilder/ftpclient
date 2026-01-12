package org.example;

import java.io.IOException;


public class Main {
    public static void main(String[] args) {

        String host = "ftp.free.fr" ;
        FTPClient client = new FTPClient();

        try {
            System.out.println("Connexion à " + host + "...");
            client.connect(host); // Établit la connexion sur le port 21

        } catch (IOException e) {
            System.err.println("Erreur de connexion : " + e.getMessage());
        }
    }
}
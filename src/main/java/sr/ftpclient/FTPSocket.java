package sr.ftpclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;


/**
 * Gère la connexion réseau bas niveau avec le serveur FTP.
 * Cette classe encapsule le socket TCP, les flux d'entrée/sortie et les timeouts.
 *
 */
public class FTPSocket {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    public static final int TIME_OUT = 5000;

    /**
     * Initialise un nouveau gestionnaire de socket FTP.
     *
     * @param host L'adresse du serveur.
     * @param port Le port de connexion.
     * @throws IOException Si une erreur survient lors de l'initialisation.
     */
    public FTPSocket(String host, int port) throws IOException {
        this.host = host;

        this.port = port;
    }



    /**
     * Ouvre la connexion TCP vers le serveur et vérifie le message de bienvenue (code 220).
     * Initialise les flux de lecture et d'écriture.
     *
     * @throws IOException Si la connexion échoue ou si le serveur renvoie une erreur.
     * @throws FTPException Si le code retour du serveur n'est pas 220.
     */
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
    /**
     * Authentifie l'utilisateur en envoyant les commandes USER et PASS.
     *
     * @param user Le nom d'utilisateur.
     * @param pass Le mot de passe.
     * @throws IOException En cas d'erreur de communication.
     * @throws FTPException Si l'utilisateur ou le mot de passe est refusé (codes autres que 331/230).
     */
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

    /**
     * Envoie une commande brute au serveur FTP (suivie d'un saut de ligne).
     *
     * @param message La commande à envoyer (ex: "USER toto").
     * @throws IOException En cas d'erreur d'écriture sur le socket.
     */
    public void write(String message) throws IOException {
        this.writer.println(message);
    }


    /**
     * Lit une ligne de réponse depuis le serveur FTP.
     *
     * @return La ligne lue.
     * @throws IOException Si la connexion est interrompue ou en cas d'erreur de lecture.
     */
    public String  read() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            throw new IOException("Connexion FTP interrompue");
        }
        return line;
    }



    /**
     * Ferme proprement les flux et le socket de connexion.
     *
     * @throws IOException En cas d'erreur lors de la fermeture.
     */
    public void close() throws IOException {
        if (writer != null) writer.close();
        if (reader != null) reader.close();
        if (socket != null) socket.close();
    }

    /**
     * Extrait le code de retour numérique (3 chiffres) d'une réponse FTP standard.
     *
     * @param response La ligne de réponse du serveur.
     * @return Le code entier (ex: 220), ou 0 si l'extraction échoue.
     */
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

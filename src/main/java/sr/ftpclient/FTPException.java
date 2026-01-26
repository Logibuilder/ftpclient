package sr.ftpclient;

import java.io.IOException;

/**
 * Exception personnalisée pour les erreurs spécifiques au protocole FTP.
 * Encapsule le code d'erreur numérique retourné par le serveur (ex: 530, 550).
 *
 * @author VotreNom
 */
public class FTPException extends IOException {
    private final int code;

    /**
     * Construit une nouvelle exception FTP.
     *
     * @param code Le code d'erreur FTP (ex: 550).
     * @param message Le message descriptif.
     */
    public FTPException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Récupère le code d'erreur FTP associé.
     *
     * @return Le code entier.
     */
    public int getCode(){
        return code;
    }

    @Override
    public String toString() {
        return "Erreur FTP " + code + " : " + this.getMessage();
    }
}
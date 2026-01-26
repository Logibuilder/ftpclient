package sr.ftpclient;

import com.google.gson.annotations.Expose;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire de parsing pour les réponses FTP et le formatage des fichiers.
 * Contient des classes internes pour représenter les informations de connexion et de fichiers.
 *
 */
public class FTPParser {


    /**
     * Représente les informations de connexion pour le mode passif (IP et Port).
     */
    public static class FtpConnectionInfo {
        private String ip;
        private int port;


        public FtpConnectionInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        public String getIp(){
            return this.ip;
        }
        public int getPort() {
            return this.port;
        }
    }

    /**
     * Représente les permissions UNIX d'un fichier (lecture, écriture, exécution).
     */
    public static class Permissions {

        public boolean read;
        public boolean write;
        public boolean execute;

        public Permissions(boolean r, boolean w, boolean x) {
            this.read = r;
            this.write = w;
            this.execute = x;
        }
    }



    /**
     * Enumération des types de fichiers supportés.
     */
    public enum TYPE {
        DIRECTORY, FILE, UNKNOWN, SYMBOLIC_LINC
    }
    /**
     * Élément de la file d'attente pour le parcours en largeur (BFS).
     */
    public static class QueueElement {
        String path;
        int level;

        public QueueElement(String path, int level) {
            this.path = path;
            this.level = level;
        }
    }
    /**
     * Représente un fichier ou un dossier distant avec ses métadonnées.
     * Cette classe est utilisée pour l'export JSON grâce à l'annotation @Expose.
     */
    public static class FileInfo {
        private final TYPE type;
        @Expose
        private String name;
        public Permissions ownerPermissions;
        public Permissions groupPermissions;
        public Permissions otherPermissions;
        //attribut pour le json
        @Expose
        private List<FileInfo> children;

        /**
         * Parse une ligne brute retournée par la commande LIST (format style UNIX).
         * @param line La ligne brute (ex: "-rw-r--r-- 1 user group 1234 Jan 01 file.txt").
         */
        public  FileInfo(String line) {
            if (line == null || line.isEmpty()) {
                this.type = TYPE.UNKNOWN;
                return;
            }

            // 1. Déterminer le type selon le premier caractère du format UNIX
            char firstChar = line.charAt(0);
            if (firstChar == 'd') {
                this.type = TYPE.DIRECTORY;
                this.children = new ArrayList<>();
            } else if (firstChar == '-') {
                this.type = TYPE.FILE;
            } else if (firstChar == 'l'){
                this.type = TYPE.SYMBOLIC_LINC;
            } else {
                this.type = TYPE.UNKNOWN;
            }

            String[] parts = line.split("\\s+");
            if (parts.length >= 9) {

                StringBuilder nameBuilder = new StringBuilder();
                for (int i = 8; i < parts.length; i++) {
                    nameBuilder.append(parts[i]).append(i == parts.length - 1 ? "" : " ");
                }
                this.name = nameBuilder.toString();
            } else {
                this.name = parts[parts.length - 1]; // Cas de secours
            }

            if (line.length() >= 10) {
                this.ownerPermissions = parsePermissions(line.substring(1, 4));
                this.groupPermissions = parsePermissions(line.substring(4, 7));
                this.otherPermissions = parsePermissions(line.substring(7, 10));
            }
        }

        private Permissions parsePermissions(String perms) {
            return new Permissions(
                    perms.charAt(0) == 'r',
                    perms.charAt(1) == 'w',
                    perms.charAt(2) == 'x'
            );
        }

        public TYPE getType(){
            return type;
        }

        public String getName(){
            return name;
        }

        public void addChild(FileInfo child) {
            if (this.children != null) {
                this.children.add(child);
            }
        }

        @Override
        public String toString() {
            return toString(0);
        }

        private String toString(int level) {
            StringBuilder sb = new StringBuilder();

            sb.append("  ".repeat(level))
                    .append("- ")
                    .append(name)
                    .append(" (")
                    .append(type)
                    .append(")")
                    .append("\n");

            if (children != null) {
                for (FileInfo child : children) {
                    sb.append(child.toString(level + 1));
                }
            }

            return sb.toString();
        }

    }

    /**
     * Transforme le texte brut renvoyé par la commande LIST en une liste d'objets FileInfo.
     *
     * @param text La réponse complète de la commande LIST.
     * @return Une liste d'objets FileInfo parsés.
     */
    public static List<FileInfo> getListFiles(String text) {
        List<FileInfo> files = new ArrayList<>();
        String[] lines = text.split("\\r?\\n"); // Gère les fins de ligne Windows et Linux

        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                files.add(new FileInfo(line));
            }
        }
        return files;
    }

    /**
     * Extrait l'IP et le port de la réponse à la commande PASV.
     * Format attendu : "227 Entering Passive Mode (h1,h2,h3,h4,p1,p2)".
     *
     * @param message La réponse du serveur.
     * @return Un objet FtpConnectionInfo contenant l'IP et le port calculé, ou null si échec.
     */
    public static FtpConnectionInfo calculerIpEtPort(String message) {
        // Extraire uniquement la partie entre parenthèses
        // On utilise une expression régulière pour capturer les 6 nombres
        Pattern pattern = Pattern.compile("\\((\\d+,\\d+,\\d+,\\d+,\\d+,\\d+)\\)");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {
            String contenu = matcher.group(1); // Ex: "212,27,60,27,109,180"
            String[] parties = contenu.split(",");

            String ip = parties[0] + "." + parties[1] + "." + parties[2] + "." + parties[3];

            // 3. Calculer le port
            int p5 = Integer.parseInt(parties[4]);
            int p6 = Integer.parseInt(parties[5]);
            int port = (p5 * 256) + p6;

            return new FtpConnectionInfo(ip, port);
        }

        return null; // Ou lever une exception si le format est mauvais
    }






}
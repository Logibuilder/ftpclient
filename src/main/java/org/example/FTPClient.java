    package org.example;

    import com.google.gson.Gson;
    import com.google.gson.GsonBuilder;

    import java.io.BufferedReader;
    import java.io.FileWriter;
    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.net.Socket;
    import java.nio.file.Path;
    import java.util.List;


    public class FTPClient {

        private FTPSocket clientSocket;

        public FTPClient() {
        }

        @FunctionalInterface
        public interface FTPAction{
            String excute() throws IOException;
        }

        public  String saffCall(FTPAction action) throws  IOException{

            String reponse = action.excute();

            while (reponse != null && reponse.length() >=3 && reponse.startsWith("1") ) {
                reponse = clientSocket.read();
            }

            if (reponse == null || reponse.length() < 3) {
                throw new FTPException(-1, "Réponse FTP invalide");
            }

            int code = Integer.parseInt(reponse.substring(0, 3));

            if (code >= 400) throw new FTPException(code, reponse.substring(3));

            return reponse;
        }

        public boolean cd(String path) throws IOException {

            String reponse =  saffCall(() -> {
                clientSocket.write("CWD " + path);
                return clientSocket.read();
            });
            return reponse.startsWith("250");
        }

        public void connect(String host,int port) throws IOException {
            this.clientSocket = new FTPSocket(host, port);
            clientSocket.connect();
            clientSocket.read();
        }

        public void login(String user, String pass) throws IOException {
            clientSocket.authenticate(user, pass);
        }

        private String  toPassifMode() throws IOException {
            this.clientSocket.write("PASV");
            return this.clientSocket.read();
        }

        public void ls() throws IOException {

            String reponsePasv = this.toPassifMode();
            FTPParser.FtpConnectionInfo conInf = FTPParser.calculerIpEtPort(reponsePasv);

            try (Socket dataSocket = new Socket(conInf.ip, conInf.port)) {

                // 3. Envoyer la commande LIST (Canal de contrôle)
                clientSocket.write("NLST");

                System.out.println(clientSocket.read());

                BufferedReader dataReader = new BufferedReader(
                        new InputStreamReader(dataSocket.getInputStream())
                );
                String ligne;
                while ((ligne = dataReader.readLine()) != null) {
                    System.out.println(ligne);
                }

                System.out.println(clientSocket.read());
            }
        }

        public void ls_l() throws IOException {
            String reponsePasv = this.toPassifMode();
            FTPParser.FtpConnectionInfo conInf = FTPParser.calculerIpEtPort(reponsePasv);

            try (Socket dataSocket = new Socket(conInf.ip, conInf.port)) {

                // 3. Envoyer la commande LIST (Canal de contrôle)
                clientSocket.write("LIST");

                System.out.println(clientSocket.read());

                BufferedReader dataReader = new BufferedReader(
                        new InputStreamReader(dataSocket.getInputStream())
                );
                String ligne;
                while ((ligne = dataReader.readLine()) != null) {
                    System.out.println(ligne);
                }

                System.out.println(clientSocket.read());
            }
        }

        public void cwd() throws IOException {
            // On envoie la commande dédiée
            this.clientSocket.write("CDUP");

            // On lit la confirmation du serveur (ex: "250 Directory successfully changed")
            this.clientSocket.read();
        }

        public List<FTPParser.FileInfo> getFile() throws IOException {
            String responsePasv = this.toPassifMode();
            FTPParser.FtpConnectionInfo conInf = FTPParser.calculerIpEtPort(responsePasv);

            StringBuilder lines = new StringBuilder();

            try (Socket dataSocket = new Socket(conInf.ip, conInf.port)) {
                clientSocket.write("LIST");
                clientSocket.read();

                BufferedReader dataReader = new BufferedReader(
                        new InputStreamReader(dataSocket.getInputStream())
                );

                String ligne;
                while ((ligne = dataReader.readLine()) != null) {
                    lines.append(ligne).append("\n");
                }
                clientSocket.read(); // Lecture du "226 Transfer complete"
            }

            return FTPParser.getListFiles(lines.toString());

        }

        public void tree() throws IOException {
            tree(0);
        }

        /**
         * Fonction AfficherArborescence(nomDossier, niveau)
         *     1. Envoyer CWD nomDossier au serveur
         *     2. Passer en mode PASV et récupérer le port
         *     3. Envoyer LIST sur le canal de contrôle
         *     4. Lire les lignes sur le canal de données :
         *        Pour chaque ligne reçue :
         *           a. Extraire le nom et le type (Dossier ou Fichier)
         *           b. Afficher avec une indentation (basée sur 'niveau')
         *           c. SI c'est un Dossier ET nom différent de "." ou ".." :
         *                AfficherArborescence(nom, niveau + 1)
         *     5. Envoyer CDUP (pour remonter au parent)
         * @throws IOException
         */
        public void tree(int level ) throws IOException {
            if (level > 10) return;
            List<FTPParser.FileInfo> fileInfos =  this.getFile();
            for (FTPParser.FileInfo f : fileInfos) {
                String name = f.getName();

                if (".".equals(name) || "..".equals(name)) continue;

                if (f.getType() == FTPParser.TYPE.DIRECTORY) {

                    System.out.print("  ".repeat(level) + "└──" +  f.getName());
                    if (f.otherPermissions.read){ //&& f.otherPermissions.write) {
                        cd(name);
                        tree(level + 1);
                        cwd();
                    } else {
                        System.out.print(" (protected)");
                    }
                    System.out.println();

                } else if (f.getType() == FTPParser.TYPE.FILE) {
                    System.out.println( "  ".repeat(level) + "├──" +  f.getName());
                } else if (f.getType() == FTPParser.TYPE.SYMBOLIC_LINC) {
                    System.out.println( "  ".repeat(level) + "├──" +  f.getName() + " (link)");
                }
            }
        }

        public FTPParser.FileInfo  treeJSON() throws IOException {
            FTPParser.FileInfo parent = new FTPParser.FileInfo("drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 /");
            return treeJSON(parent);
        }

        public FTPParser.FileInfo treeJSON(FTPParser.FileInfo current) throws IOException {

            if (current.getType() != FTPParser.TYPE.DIRECTORY
                    || current.otherPermissions == null
                    || !current.otherPermissions.read
                    || !current.otherPermissions.execute) {
                return current;
            }

            this.cd(current.getName());
            List<FTPParser.FileInfo> children =  this.getFile();

            for (FTPParser.FileInfo f : children) {
                String name = f.getName();

                if (".".equals(name) || "..".equals(name)) continue;

                if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                    current.addChild(f);
                    treeJSON(f);
                } else if (f.getType() == FTPParser.TYPE.FILE || f.getType() == FTPParser.TYPE.SYMBOLIC_LINC) {
                    current.addChild(f);
                }
            }
            this.cwd();
            Gson gson = new GsonBuilder()
                    .excludeFieldsWithoutExposeAnnotation()
                    .setPrettyPrinting()
                    .create();

            String json = gson.toJson(current);
            System.out.println(json);

            Path output = Path.of(System.getProperty("user.dir"), "ftp_tree.json");

            try (FileWriter writer = new FileWriter(output.toFile())) {
                writer.write(json);
            }

            System.out.println("JSON téléchargé dans : " + output.toAbsolutePath());

            return current;
        }

        public void disconnect() throws IOException {
            clientSocket.close();
        }


    }

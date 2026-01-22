    package sr.ftpclient;
    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.net.Socket;

    import java.util.*;


    public class FTPClient {

        private FTPSocket clientSocket;
        private Commande commande = new Commande();

        public void setCommande(Commande commande) {
            this.commande = commande;
        }

        public FTPClient() {
        }

        public boolean cd(String path) throws IOException {

            clientSocket.write("CWD " + path);
            String reponse =  clientSocket.read();
            return reponse.startsWith("250");
        }

        public void connect(String host,int port) throws IOException {
            this.clientSocket = new FTPSocket(host, port);
            clientSocket.connect();
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

        public boolean cwd() throws IOException {
            this.clientSocket.write("CDUP");
            String reponse = this.clientSocket.read();
            return reponse != null && reponse.startsWith("250");
        }

        public String pwd() throws IOException {
            clientSocket.write("PWD");
            String res = clientSocket.read();

            if (res != null && res.startsWith("257")) {
                int firstQuote = res.indexOf('\"');
                int lastQuote = res.lastIndexOf('\"');

                if (firstQuote != -1 && lastQuote != -1 && firstQuote != lastQuote) {
                    String path = res.substring(firstQuote + 1, lastQuote);
                    return path.replace("\"\"", "\"");
                }
            }
            return null;
        }

        public List<FTPParser.FileInfo> getFile() throws IOException {
            String responsePasv = this.toPassifMode();

            FTPParser.FtpConnectionInfo conInfo = FTPParser.calculerIpEtPort(responsePasv);

            if (conInfo == null) {
                System.err.println("Erreur PASV : Impossible de se connecter en mode passif.");
                System.err.println("Réponse du serveur : " + responsePasv);
                return new ArrayList<>();
            }

            StringBuilder lines = new StringBuilder();

            try (Socket dataSocket = new Socket(conInfo.ip, conInfo.port)) {
                clientSocket.write("LIST");
                String responseList = clientSocket.read();

                if (!responseList.startsWith("1")) {
                    System.out.println("Erreur lors du LIST : " + responseList);
                    return new ArrayList<>();
                }

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

        public void treeDFS() throws IOException {
            treeDFS(0);
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
        public void treeDFS(int level) throws IOException {
            //option profondeur max
            int maxDepth = this.commande.getMaxDepth();
            if (maxDepth != -1 && level > maxDepth) return;

            List<FTPParser.FileInfo> fileInfos =  this.getFile();
            for (FTPParser.FileInfo f : fileInfos) {
                String name = f.getName();

                if (".".equals(name) || "..".equals(name)) continue;
                if (f.getType() == FTPParser.TYPE.SYMBOLIC_LINC) {
                    continue;
                }

                if (this.commande.isDirsOnly() && f.getType() != FTPParser.TYPE.DIRECTORY) {
                    continue; // On n'affiche pas les fichiers
                }
                if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                    System.out.println("  ".repeat(level) + "└──" +  f.getName());
                    if (this.cd(name)) {
                        try {
                            treeDFS(level + 1);
                        } finally {
                            if (!this.cwd()) {
                                System.err.println("Erreur: impossible de remonter depuis " + name);
                            }
                        }
                    } else {
                        System.out.println("  ".repeat(level + 1) + " (protected)");
                    }
                } else if (f.getType() == FTPParser.TYPE.FILE) {
                    System.out.println( "  ".repeat(level) + "├──" +  f.getName());
                }
            }
        }

        /**
         * Fonction treeBFS() (Parcours en Largeur)
         * 1. Initialiser une File (Queue) contenant la racine "/" et le niveau 0.
         * 2. TANT QUE la File n'est pas vide :
         * a. Défiler (poll) l'élément courant (cheminDossier, niveau).
         * b. SI niveau > maxDepth, passer au suivant.
         * c. Envoyer CWD cheminDossier (chemin absolu) au serveur.
         * d. Passer en mode PASV et récupérer le port.
         * e. Envoyer LIST sur le canal de contrôle.
         * f. Lire les lignes sur le canal de données :
         * Pour chaque ligne reçue :
         * i.   Extraire le nom et le type.
         * ii.  Afficher le fichier/dossier.
         * iii. SI c'est un Dossier ET nom différent de "." ou ".." :
         * Construire le chemin absolu de l'enfant.
         * Enfiler (add) l'enfant dans la File avec (niveau + 1).
         * @throws IOException
         */
        public void treeBFS() throws IOException {
            treeBFS(0);
        }

        public void treeBFS(int level) throws IOException{
            int maxDepth = this.commande.getMaxDepth();

            Queue<FTPParser.QueueElement> queue = new LinkedList<>();
            queue.add(new FTPParser.QueueElement("/", 0));

            while (!queue.isEmpty()) {
                FTPParser.QueueElement current = queue.poll();

                if (maxDepth != -1 && current.level > maxDepth) {
                    continue;
                }

                if (!this.cd(current.path)) continue;
                String indatation = " ".repeat(level);
                System.out.println(indatation + "└──"+ current);

                List<FTPParser.FileInfo> files =  this.getFile();

                for (FTPParser.FileInfo f : files ) {
                    String name = f.getName();
                    if (".".equals(name) || "..".equals(name)) continue;

                    if (this.commande.isDirsOnly() && f.getType() != FTPParser.TYPE.DIRECTORY) {
                        continue; // On n'affiche pas les fichiers
                    }

                    if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                        System.out.println(indatation + "  └──" + name);

                        String separator = current.path.endsWith("/") ? "" : "/";
                        String childPath = current.path + separator + name;
                        queue.add(new FTPParser.QueueElement(childPath, current.level + 1));

                    } else {
                        System.out.println(indatation +"  ├──" + name);
                    }
                }

            }
        }

        public FTPParser.FileInfo  treeJSON() throws IOException {
            FTPParser.FileInfo parent = new FTPParser.FileInfo("drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 /");
            return treeJSON(parent, 0);
        }


        public FTPParser.FileInfo treeJSON(FTPParser.FileInfo current, int level) throws IOException {

            if (level >= this.commande.getMaxDepth()) return current;

            if (current.getType() != FTPParser.TYPE.DIRECTORY
                    || current.otherPermissions == null
                    || !current.otherPermissions.read) {
                return current;
            }

            if (!this.cd(current.getName())) {
                return current;
            }
            try {
                List<FTPParser.FileInfo> children = this.getFile();

                for (FTPParser.FileInfo f : children) {
                    String name = f.getName();
                    if (".".equals(name) || "..".equals(name)) continue;
                    if (!this.commande.isDirsOnly() ) {
                        current.addChild(f);
                    } else if (f.getType() == FTPParser.TYPE.DIRECTORY){
                        current.addChild(f);
                    }

                    if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                        treeJSON(f, level + 1);
                    }
                }
            } finally {
                this.cwd();
            }
            return current;
        }




        public void disconnect() throws IOException {
            clientSocket.close();
        }


    }

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
            if (this.clientSocket == null) {
                this.clientSocket = new FTPSocket(host, port);
            }
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
                throw new IOException("Erreur PASV : " + responsePasv);
            }

            StringBuilder lines = new StringBuilder();

            try (Socket dataSocket = new Socket(conInfo.ip, conInfo.port)) {
                clientSocket.write("LIST");
                String responseList = clientSocket.read();

                if (!responseList.startsWith("1") && !responseList.startsWith("2")) {
                    System.out.println("Erreur lors du LIST : " + responseList);
                    throw new IOException("Erreur lors du LIST : " + responseList);
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
            treeDFS("/", 0);
        }

        /**
         *  Fonction AfficherArborescence(nomDossier, niveau)
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
        public void treeDFS(String currentPath, int level) throws IOException {
            //option profondeur max
            int maxDepth = this.commande.getMaxDepth();
            if (maxDepth != -1 && level > maxDepth) return;

            List<FTPParser.FileInfo> fileInfos =  new ArrayList<>();

            boolean getFilesuccess = false;
            int nbTry = 0;

            while (!getFilesuccess && nbTry < 3) {
                try {
                    fileInfos = getFile();
                    getFilesuccess = true;
                } catch (IOException e) {
                    nbTry++;
                    System.err.println("Erreur. Reconnexion...");
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}

                    // REPRISE D'ÉTAT (State Recovery)
                    try {
                        this.reconnect();
                        // On retourne dans le dossier où on était avant le crash !
                        // Comme on a reconnecté, on est à la racine, il faut faire un CWD vers le chemin absolu
                        if (!this.cd(currentPath)) {
                            System.err.println("Impossible de retourner dans " + currentPath);
                            return; // Si on ne peut pas y retourner, on abandonne cette branche
                        }
                    } catch (IOException ignored) {}
                }
            }

            if (!getFilesuccess) return;

            for (FTPParser.FileInfo f : fileInfos) {
                String name = f.getName();

                String separator = currentPath.endsWith("/") ? "" : "/";
                String nextPath = currentPath + separator + name;

                if (".".equals(name) || "..".equals(name)) continue;
                if (f.getType() == FTPParser.TYPE.SYMBOLIC_LINC) {
                    continue;
                }

                if (this.commande.isDirsOnly() && f.getType() != FTPParser.TYPE.DIRECTORY) {
                    continue; // On n'affiche pas les fichiers
                }
                if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                    System.out.println("  ".repeat(level) + "└──" +  f.getName());

                    boolean cdSuccess = false;
                    nbTry = 0;
                    while (!cdSuccess && nbTry < 3) {
                        try {
                            /// /////////////////////////
                            if (this.cd(name)) {
                                try {
                                    treeDFS(nextPath, level + 1);
                                } finally {
                                    try {
                                        // On tente de remonter
                                        this.cwd();
                                    } catch (Exception e) {
                                        // Si ça échoue (réseau coupé, socket null...), on ne fait RIEN.
                                        // On évite ainsi le crash (NullPointerException) qui masque la vraie erreur.
                                    }
                                }
                            } else {
                                System.out.println("  ".repeat(level + 1) + " (protected)");
                            }
                            /// ///////////////////////////////////
                            cdSuccess = true;
                        } catch (IOException e) {
                            nbTry++;
                            System.err.println("Erreur CD " + name + " (" + e.getMessage() + "). Réparation...");
                            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                            try {
                                this.reconnect();
                                // On retourne au dossier PARENT (currentPath) pour pouvoir retenter d'entrer dans l'enfant
                                if (!this.cd(currentPath)) throw new IOException("Impossible de restaurer le parent " + currentPath);
                            } catch (IOException ignored) { }
                        }
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
                /// /////////////////// si panne
                boolean success = false;
                int nbTry = 0;
                while (!success && nbTry < 3) {
                    try {
                        if (!this.cd(current.path)) break;
                        String indatation = " ".repeat(level);
                        System.out.println(indatation + "└──" + current);

                        List<FTPParser.FileInfo> files = this.getFile();

                        for (FTPParser.FileInfo f : files) {
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
                                System.out.println(indatation + "  ├──" + name);
                            }
                        }

                        success =true;
                    } catch (IOException e) {
                        nbTry++;

                        System.out.println("Erreur réseau : " + e.getMessage());
                        try { Thread.sleep(2000); } catch (InterruptedException inter) {}
                        try {
                            this.reconnect();
                        } catch (IOException ignored) {}

                    }
                }

            }
        }

        public FTPParser.FileInfo treeJSON() throws IOException {
            // On initialise la racine
            FTPParser.FileInfo parent = new FTPParser.FileInfo("drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 /");
            // On lance la récursion avec le chemin absolu "/"
            return treeJSON(parent, "/", 0);
        }

        public FTPParser.FileInfo treeJSON(FTPParser.FileInfo current, String currentPath, int level) throws IOException {
            //Vérifications de base (Profondeur, Permissions)
            if (level >= this.commande.getMaxDepth()) return current;

            if (current.getType() != FTPParser.TYPE.DIRECTORY
                    || current.otherPermissions == null
                    || !current.otherPermissions.read) {
                return current;
            }

            // On essaie de faire "cd nomDossier". Si ça rate, on doit revenir au PARENT pour réessayer.
            boolean entered = false;
            int retries = 0;

            while (!entered && retries < 3) {
                try {
                    if (this.cd(current.getName())) {
                        entered = true;
                    } else {
                        return current; // Impossible d'entrer (droits ?)
                    }
                } catch (IOException e) {
                    retries++;
                    System.err.println("Erreur CD JSON (" + e.getMessage() + "). Réparation...");
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                    try {
                        this.reconnect();
                        //On doit revenir au PARENT de currentPath
                        String parentPath = "/";
                        int lastSlash = currentPath.lastIndexOf('/');
                        if (lastSlash > 0) {
                            parentPath = currentPath.substring(0, lastSlash);
                        }
                        // Si on est à la racine, le parent est "/"
                        if (currentPath.equals("/")) parentPath = "/";

                        if (!this.cd(parentPath)) {
                            throw new IOException("Impossible de restaurer le parent " + parentPath);
                        }
                    } catch (IOException ignored) {}
                }
            }

            if (!entered) return current; // Échec total de navigation

            try {
                // RÉCUPÉRATION DES FICHIERS (Retry Loop)
                // Maintenant qu'on est dedans, si getFile rate, on doit revenir à currentPath
                List<FTPParser.FileInfo> children = new ArrayList<>();
                boolean listed = false;
                retries = 0;

                while (!listed && retries < 3) {
                    try {
                        children = this.getFile();
                        listed = true;
                    } catch (IOException e) {
                        retries++;
                        System.err.println("Erreur Listing JSON (" + e.getMessage() + "). Réparation...");
                        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

                        try {
                            this.reconnect();
                            // RECOVERY : On doit revenir DANS le dossier courant
                            if (!this.cd(currentPath)) {
                                throw new IOException("Impossible de restaurer " + currentPath);
                            }
                        } catch (IOException ignored) {}
                    }
                }

                if (!listed) return current;

                // 4. TRAITEMENT DES ENFANTS
                for (FTPParser.FileInfo f : children) {
                    String name = f.getName();
                    if (".".equals(name) || "..".equals(name)) continue;

                    // Ajout à l'arbre JSON
                    if (!this.commande.isDirsOnly() || f.getType() == FTPParser.TYPE.DIRECTORY) {
                        current.addChild(f);
                    }

                    // Récursion
                    if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                        String separator = currentPath.endsWith("/") ? "" : "/";
                        String nextPath = currentPath + separator + name;

                        treeJSON(f, nextPath, level + 1);
                    }
                }
            } finally {
                // 5. SORTIE SÉCURISÉE
                try {
                    this.cwd(); // CDUP
                } catch (Exception e) {
                    // On ignore l'erreur ici pour ne pas faire planter le programme
                    // La prochaine boucle détectera si la connexion est morte
                }
            }

            return current;
        }




        public void disconnect() throws IOException {
            clientSocket.close();
        }


        // reconnect pour les pannes
        private void reconnect() throws IOException{
            System.out.println(">>Connection perdu. Tentative de reconnection...");

            try {
                this.disconnect();
            } catch (IOException e) {

            }
            FTPSocket tmpSocket = new FTPSocket(commande.getHost(), 21);

            tmpSocket.connect();

            this.clientSocket = tmpSocket;
            //this.connect(commande.getHost(), 21);
            this.login(commande.getLogin(), commande.getPassword());
        }


    }

    package sr.ftpclient;
    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.net.Socket;

    import java.util.*;

    /**
     * Client FTP principal gérant la logique métier des échanges avec le serveur.
     * Cette classe implémente les commandes de navigation (CD, PWD), de listage (LIST, NLST)
     * et les algorithmes de parcours d'arborescence (DFS, BFS).
     * Elle gère également la tolérance aux pannes réseau via des mécanismes de reconnexion.
     *
     */
    public class FTPClient {

        private FTPSocket clientSocket;
        private Commande commande = new Commande();
        /**
         * Définit la configuration de la commande en cours.
         *
         * @param commande L'objet contenant les paramètres d'exécution (hôte, login, options...).
         */
        public void setCommande(Commande commande) {
            this.commande = commande;
        }
        /**
         * Constructeur par défaut.
         */
        public FTPClient() {
        }
        /**
         * Change le répertoire courant sur le serveur FTP (commande CWD).
         *
         * @param path Le chemin relatif ou absolu du dossier cible.
         * @return true si le changement a réussi (code 250), false sinon.
         * @throws IOException En cas d'erreur de communication.
         */
        public boolean cd(String path) throws IOException {

            clientSocket.write("CWD " + path);
            String reponse =  clientSocket.read();
            return reponse.startsWith("250");
        }

        /**
         * Établit la connexion TCP avec le serveur FTP.
         * Si un socket existe déjà (cas des tests unitaires avec injection), il est réutilisé.
         *
         * @param host L'adresse du serveur (nom de domaine ou IP).
         * @param port Le port de connexion (généralement 21).
         * @throws IOException En cas d'échec de la connexion socket.
         */
        public void connect(String host,int port) throws IOException {
            if (this.clientSocket == null) {
                this.clientSocket = new FTPSocket(host, port);
            }
            clientSocket.connect();
        }

        /**
         * Authentifie l'utilisateur auprès du serveur FTP (commandes USER et PASS).
         *
         * @param user Le nom d'utilisateur.
         * @param pass Le mot de passe.
         * @throws IOException En cas d'échec d'authentification ou d'erreur réseau.
         */
        public void login(String user, String pass) throws IOException {
            clientSocket.authenticate(user, pass);
        }
        /**
         * Active le mode passif (commande PASV) et récupère la réponse du serveur.
         *
         * @return La réponse brute du serveur contenant l'IP et le port pour la connexion de données.
         * @throws IOException En cas d'erreur réseau.
         */
        private String  toPassifMode() throws IOException {
            this.clientSocket.write("PASV");
            return this.clientSocket.read();
        }
        /**
         * Affiche le contenu du répertoire courant (liste simple des noms).
         * Utilise la commande NLST et le mode passif.
         *
         * @throws IOException En cas d'erreur lors du transfert de données.
         */
        public void ls() throws IOException {

            String reponsePasv = this.toPassifMode();
            FTPParser.FtpConnectionInfo conInf = FTPParser.calculerIpEtPort(reponsePasv);

            try (Socket dataSocket = new Socket(conInf.getIp(), conInf.getPort())) {

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

        /**
         * Affiche le contenu détaillé du répertoire courant (permissions, taille, date...).
         * Utilise la commande LIST et le mode passif.
         *
         * @throws IOException En cas d'erreur lors du transfert de données.
         */
        public void ls_l() throws IOException {
            String reponsePasv = this.toPassifMode();
            FTPParser.FtpConnectionInfo conInf = FTPParser.calculerIpEtPort(reponsePasv);

            try (Socket dataSocket = new Socket(conInf.getIp(), conInf.getPort())) {

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

        /**
         * Remonte au répertoire parent (commande CDUP).
         *
         * @return true si la remontée a réussi, false sinon.
         * @throws IOException En cas d'erreur réseau.
         */
        public boolean cwd() throws IOException {
            this.clientSocket.write("CDUP");
            String reponse = this.clientSocket.read();
            return reponse != null && reponse.startsWith("250");
        }
        /**
         * Récupère le chemin absolu du répertoire courant (commande PWD).
         *
         * @return Le chemin courant sous forme de chaîne, ou null si l'extraction échoue.
         * @throws IOException En cas d'erreur réseau.
         */
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
        /**
         * Récupère la liste structurée des fichiers et dossiers du répertoire courant.
         * Cette méthode parse la réponse de la commande LIST pour créer des objets FileInfo.
         *
         * @return Une liste d'objets {@link FTPParser.FileInfo} représentant le contenu du dossier.
         * @throws IOException En cas d'erreur réseau ou si le mode passif échoue.
         */
        public List<FTPParser.FileInfo> getFile() throws IOException {
            String responsePasv = this.toPassifMode();

            FTPParser.FtpConnectionInfo conInfo = FTPParser.calculerIpEtPort(responsePasv);

            if (conInfo == null) {
                System.err.println("Erreur PASV : Impossible de se connecter en mode passif.");
                System.err.println("Réponse du serveur : " + responsePasv);
                throw new IOException("Erreur PASV : " + responsePasv);
            }

            StringBuilder lines = new StringBuilder();

            try (Socket dataSocket = new Socket(conInfo.getIp(), conInfo.getPort())) {
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
        /**
         * Lance le parcours de l'arborescence en profondeur (DFS) depuis la racine ("/").
         *
         * @throws IOException En cas d'erreur non récupérable.
         */
        public void treeDFS() throws IOException {
            treeDFS("/", 0);
        }

//        /**
//         *  Fonction AfficherArborescence(nomDossier, niveau)
//         *     1. Envoyer CWD nomDossier au serveur
//         *     2. Passer en mode PASV et récupérer le port
//         *     3. Envoyer LIST sur le canal de contrôle
//         *     4. Lire les lignes sur le canal de données :
//         *        Pour chaque ligne reçue :
//         *           a. Extraire le nom et le type (Dossier ou Fichier)
//         *           b. Afficher avec une indentation (basée sur 'niveau')
//         *           c. SI c'est un Dossier ET nom différent de "." ou ".." :
//         *                AfficherArborescence(nom, niveau + 1)
//         *     5. Envoyer CDUP (pour remonter au parent)
//         * @throws IOException
//         */
        /**
         * Parcours récursif de l'arborescence en profondeur (DFS).
         * Affiche l'arborescence dans la console avec une indentation appropriée.
         * Gère la tolérance aux pannes réseau avec des tentatives de reconnexion.
         *
         * @param currentPath Le chemin absolu du dossier en cours d'exploration.
         * @param level Le niveau de profondeur actuel (pour l'indentation).
         * @throws IOException En cas d'erreur réseau persistante après plusieurs tentatives.
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

//        /**
//         * Fonction treeBFS() (Parcours en Largeur)
//         * 1. Initialiser une File (Queue) contenant la racine "/" et le niveau 0.
//         * 2. TANT QUE la File n'est pas vide :
//         * a. Défiler (poll) l'élément courant (cheminDossier, niveau).
//         * b. SI niveau > maxDepth, passer au suivant.
//         * c. Envoyer CWD cheminDossier (chemin absolu) au serveur.
//         * d. Passer en mode PASV et récupérer le port.
//         * e. Envoyer LIST sur le canal de contrôle.
//         * f. Lire les lignes sur le canal de données :
//         * Pour chaque ligne reçue :
//         * i.   Extraire le nom et le type.
//         * ii.  Afficher le fichier/dossier.
//         * iii. SI c'est un Dossier ET nom différent de "." ou ".." :
//         * Construire le chemin absolu de l'enfant.
//         * Enfiler (add) l'enfant dans la File avec (niveau + 1).
//         * @throws IOException
//         */
        /**
         * Lance le parcours de l'arborescence en largeur (BFS) depuis la racine ("/").
         *
         * @throws IOException En cas d'erreur critique.
         */
        public void treeBFS() throws IOException {
            treeBFS(0);
        }

        /**
         * Parcours itératif de l'arborescence en largeur (BFS) utilisant une file d'attente.
         * Affiche l'arborescence niveau par niveau.
         *
         * @param level Le niveau de départ (0).
         * @throws IOException En cas d'erreur réseau persistante.
         */
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

        /**
         * Lance la construction de l'arbre JSON depuis la racine ("/").
         *
         * @return L'objet racine {@link FTPParser.FileInfo} contenant toute la structure.
         * @throws IOException En cas d'erreur critique.
         */
        public FTPParser.FileInfo treeJSON() throws IOException {
            // On initialise la racine
            FTPParser.FileInfo parent = new FTPParser.FileInfo("drwxr-xr-x 1 ftp ftp 0 Jan 01 00:00 /");
            // On lance la récursion avec le chemin absolu "/"
            return treeJSON(parent, "/", 0);
        }

        /**
         * Construit récursivement une structure d'objets représentant l'arborescence (pour export JSON).
         *
         * @param current Le noeud courant (dossier) à peupler.
         * @param currentPath Le chemin absolu du dossier courant.
         * @param level Le niveau de profondeur actuel.
         * @return L'objet FileInfo mis à jour avec ses enfants.
         * @throws IOException En cas d'erreur réseau persistante.
         */
        public FTPParser.FileInfo treeJSON(FTPParser.FileInfo current, String currentPath, int level) throws IOException {
            //Vérifications de base (Profondeur, Permissions)
            if (this.commande.getMaxDepth() > 0 && level >= this.commande.getMaxDepth()) return current;

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

        /**
         * Ferme la connexion avec le serveur FTP.
         *
         * @throws IOException En cas d'erreur lors de la fermeture du socket.
         */
        public void disconnect() throws IOException {
            clientSocket.close();
        }


        /**
         * Tente de reconnecter le client au serveur suite à une perte de connexion.
         * Ferme l'ancienne connexion, en ouvre une nouvelle et ré-authentifie l'utilisateur.
         *
         * @throws IOException Si la reconnexion échoue totalement.
         */
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

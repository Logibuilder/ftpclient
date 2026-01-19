    package sr.ftpclient;

    import com.google.gson.Gson;
    import com.google.gson.GsonBuilder;

    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.net.Socket;

    import java.util.HashSet;
    import java.util.List;
    import java.util.Set;


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
            tree("/", 0, new HashSet<>());
        }

        private void tree(String path, int level, Set<String> visited) throws IOException {
            if (level > 10) {
                System.out.println("  ".repeat(level) + "(Profondeur maximale atteinte)");
                return;
            }

            // Sauvegarder le répertoire courant ORIGINAL
            String originalPwd = this.pwd();

            try {
                // Se déplacer dans le répertoire à explorer
                if (!this.cd(path)) {
                    System.out.println("  ".repeat(level) + "└── " + path + " (accès refusé)");
                    return;
                }

                String currentPath = this.pwd();
                if (visited.contains(currentPath)) {
                    System.out.println("  ".repeat(level) + "└── " + path + " (déjà visité)");
                    return;
                }
                visited.add(currentPath);

                // Lister les fichiers
                List<FTPParser.FileInfo> fileInfos = this.getFile();

                for (FTPParser.FileInfo f : fileInfos) {
                    String name = f.getName();

                    if (".".equals(name) || "..".equals(name)) continue;

                    if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                        System.out.println("  ".repeat(level) + "└── " + name);

                        // Explorer le sous-répertoire
                        tree(name, level + 1, visited);

                    } else if (f.getType() == FTPParser.TYPE.FILE) {
                        System.out.println("  ".repeat(level) + "├── " + name);
                    } else if (f.getType() == FTPParser.TYPE.SYMBOLIC_LINC) {
                        System.out.println("  ".repeat(level) + "├── " + name + " (link)");
                    }
                }
            } finally {
                // TOUJOURS revenir au répertoire original
                if (originalPwd != null) {
                    this.cd(originalPwd);
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
                    || !current.otherPermissions.read) {
                return current;
            }

            if (!this.cd(current.getName())) {
                return current;
            }
            List<FTPParser.FileInfo> children =  this.getFile();

            for (FTPParser.FileInfo f : children) {
                String name = f.getName();
                if (".".equals(name) || "..".equals(name)) continue;

                current.addChild(f);

                if (f.getType() == FTPParser.TYPE.DIRECTORY) {
                    treeJSON(f);
                }
            }
            return current;
        }

        public void disconnect() throws IOException {
            clientSocket.close();
        }


    }

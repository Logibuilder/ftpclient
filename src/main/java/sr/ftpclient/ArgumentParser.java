package sr.ftpclient;


public class ArgumentParser {

    public static Commande parse(String[] args) {
        Commande options = new Commande();

        if (args.length == 0) {
            return options;
        }

        options.setHost(args[0]);

        int i = 1;
        while (i < args.length) {
            String arg = args[i];

            // Options avec préfixe - ou --
            if (arg.startsWith("-")) {
                switch (arg) {
                    // Niveau 12-13 : Profondeur
                    case "-d":
                    case "--depth":
                    case "-L":
                        if (i + 1 < args.length) {
                            options.setMaxDepth(Integer.parseInt(args[++i]));
                        }
                        break;

                    // Niveau 13-14 : Format de sortie
                    case "-o":
                    case "--output":
                        if (i + 1 < args.length) {
                            String format = args[++i].toLowerCase();
                            if ("json".equals(format)) {
                                options.setOutputFormat(Commande.OutputFormat.JSON);
                            }
                        }
                        break;

                    // Niveau 14-15 : Mode de parcours
                    case "--bfs":
                        options.setTraversalMode(Commande.TraversalMode.BFS);
                        break;
                    case "--dfs":
                        options.setTraversalMode(Commande.TraversalMode.DFS);
                        break;

                    // Niveau 15-16 : Reprise sur panne
                    case "--resume":
                        options.setEnableResume(true);
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            options.setResumeFile(args[++i]);
                        }
                        break;
                    case "--save-progress":
                        options.setSaveProgress(true);
                        break;

                    // Niveau >16 : Options style tree
                    case "--dirs-only":
                        options.setDirsOnly(true);
                        break;
                    case "-f":
                    case "--full-path":
                        options.setFullPath(true);
                        break;
                    case "-P":
                        if (i + 1 < args.length) {
                            options.setIncludePattern(args[++i]);
                        }
                        break;
                    case "-I":
                        if (i + 1 < args.length) {
                            options.setExcludePattern(args[++i]);
                        }
                        break;
                    case "--dirsfirst":
                        options.setDirsFirst(true);
                        break;
                    case "-h":
                    case "--human-readable":
                        options.setHumanReadable(true);
                        break;
                    case "-s":
                    case "--size":
                        options.setShowSize(true);
                        break;
                    case "--help":
                        printHelp();
                        System.exit(0);
                        break;

                    default:
                        System.err.println("Option inconnue : " + arg);
                        printHelp();
                        System.exit(1);
                }
            } else {
                // Arguments positionnels (login et password)
                if (i == 1) {
                    options.setLogin(arg);
                } else if (i == 2) {
                    options.setPassword(arg);
                }
            }
            i++;
        }

        return options;
    }

    private static void printHelp() {
        System.out.println("Usage: java -jar TreeFTP.jar <host> [login] [password] [OPTIONS]");
        System.out.println();
        System.out.println("Arguments positionnels:");
        System.out.println("  <host>              Adresse du serveur FTP (obligatoire)");
        System.out.println("  [login]             Nom d'utilisateur (défaut: anonymous)");
        System.out.println("  [password]          Mot de passe (défaut: anonymous)");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --depth <N>     Profondeur maximale d'exploration");
        System.out.println("  -L <N>              Alias pour --depth");
        System.out.println("  -o, --output <fmt>  Format de sortie: text|json (défaut: text)");
        System.out.println("  --bfs               Parcours en largeur");
        System.out.println("  --dfs               Parcours en profondeur (défaut)");
        System.out.println("  --resume [file]     Reprendre depuis un fichier de progression");
        System.out.println("  --save-progress     Sauvegarder la progression");
        System.out.println();
        System.out.println("Options avancées (style tree):");
        System.out.println("  --dirs-only         Afficher uniquement les dossiers");
        System.out.println("  -f, --full-path     Afficher les chemins complets");
        System.out.println("  -P <pattern>        Inclure uniquement les fichiers correspondants");
        System.out.println("  -I <pattern>        Exclure les fichiers correspondants");
        System.out.println("  --dirsfirst         Lister les dossiers en premier");
        System.out.println("  -h, --human-readable Tailles lisibles");
        System.out.println("  -s, --size          Afficher les tailles");
        System.out.println("  --help              Afficher cette aide");
        System.out.println();
        System.out.println("Exemples:");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com anonymous pass -d 3");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com -o json --bfs");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com -d 5 --save-progress");
    }
}
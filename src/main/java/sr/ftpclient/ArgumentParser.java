package sr.ftpclient;

/**
 * Gère l'analyse des arguments de la ligne de commande.
 * Convertit le tableau de chaînes en un objet de configuration {@link Commande}.
 *
 */
public class ArgumentParser {
    /**
     * Analyse les arguments passés au programme.
     *
     * @param args Les arguments bruts.
     * @return Un objet Commande configuré avec les options détectées.
     */
    public static Commande parse(String[] args) {
        Commande options = new Commande();

        if (args.length == 0) {
            return options;
        }

        options.setHost(args[0]);

        int i = 1;
        while (i < args.length) {
            String arg = args[i];

            if (arg.startsWith("-")) {
                switch (arg) {
                    case "-d":
                    case "--depth":
                    case "-L":
                        if (i + 1 < args.length) {
                            options.setMaxDepth(Integer.parseInt(args[++i]));
                        }
                        break;

                    case "-o":
                    case "--output":
                        if (i + 1 < args.length) {
                            String format = args[++i].toLowerCase();
                            if ("json".equals(format)) {
                                options.setOutputFormat(Commande.OutputFormat.JSON);
                            }
                        }
                        break;

                    case "--bfs":
                        options.setTraversalMode(Commande.TraversalMode.BFS);
                        break;
                    case "--dfs":
                        options.setTraversalMode(Commande.TraversalMode.DFS);
                        break;

                    case "--dirs-only":
                        options.setDirsOnly(true);
                        break;

                    case "--help":
                        printHelp();
                        System.exit(0);
                        break;
                    case "-l":
                        options.setLongListing(true);
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
    /**
     * Affiche l'aide d'utilisation du programme dans la console standard.
     */
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
        System.out.println();
        System.out.println("Options avancées (style tree):");
        System.out.println("  --dirs-only         Afficher uniquement les dossiers");
        System.out.println("  --help              Afficher cette aide");
        System.out.println();
        System.out.println("Exemples:");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com anonymous pass");
        System.out.println("  java -jar TreeFTP.jar ftp.ubuntu.com anonymous pass -o json");
    }
}


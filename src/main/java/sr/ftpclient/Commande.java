package sr.ftpclient;

/**
 * Classe de configuration contenant les paramètres d'exécution du client FTP.
 * Stocke les informations de connexion, les préférences d'affichage et les modes de parcours.
 */
public class Commande {
    private String host = "";
    private String login = "";
    private String password = "sr_ftp";

    private int maxDepth = -1; // -1 = illimité

    private OutputFormat outputFormat = OutputFormat.TEXT;

    private TraversalMode traversalMode = null;

    private boolean longListing = false; // Par défaut false (ls simple)

    // Niveau >16 (options style tree)
    private boolean dirsOnly = false;

    /**
     * Format de sortie des données.
     */
    public enum OutputFormat {
        TEXT, JSON
    }
    /**
     * Mode de parcours de l'arborescence.
     */
    public enum TraversalMode {
        DFS, BFS
    }

    // Getters et setters
    public boolean isLongListing() { return longListing; }
    public void setLongListing(boolean longListing) { this.longListing = longListing; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public String getLogin() { return login; }
    public void setLogin(String login) { this.login = login; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }

    public OutputFormat getOutputFormat() { return outputFormat; }
    public void setOutputFormat(OutputFormat format) { this.outputFormat = format; }

    public TraversalMode getTraversalMode() { return traversalMode; }
    public void setTraversalMode(TraversalMode mode) { this.traversalMode = mode; }


    public boolean isDirsOnly() { return dirsOnly; }
    public void setDirsOnly(boolean dirsOnly) { this.dirsOnly = dirsOnly; }


}

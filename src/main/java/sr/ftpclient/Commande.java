package sr.ftpclient;

public class Commande {
    private String host = "";
    private String login = "";
    private String password = "sr_ftp";

    private int maxDepth = -1; // -1 = illimité

    private OutputFormat outputFormat = OutputFormat.TEXT;

    private TraversalMode traversalMode = null;

    private boolean enableResume = false;
    private String resumeFile = null;
    private boolean saveProgress = false;
    private boolean longListing = false; // Par défaut false (ls simple)

    // Niveau >16 (options style tree)
    private boolean dirsOnly = false;
    private boolean fullPath = false;
    private String includePattern = null;
    private String excludePattern = null;
    private boolean dirsFirst = false;
    private boolean humanReadable = false;
    private boolean showSize = false;

    public enum OutputFormat {
        TEXT, JSON
    }

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

    public boolean isEnableResume() { return enableResume; }
    public void setEnableResume(boolean enable) { this.enableResume = enable; }

    public String getResumeFile() { return resumeFile; }
    public void setResumeFile(String file) { this.resumeFile = file; }

    public boolean isSaveProgress() { return saveProgress; }
    public void setSaveProgress(boolean save) { this.saveProgress = save; }

    public boolean isDirsOnly() { return dirsOnly; }
    public void setDirsOnly(boolean dirsOnly) { this.dirsOnly = dirsOnly; }

    public boolean isFullPath() { return fullPath; }
    public void setFullPath(boolean fullPath) { this.fullPath = fullPath; }

    public String getIncludePattern() { return includePattern; }
    public void setIncludePattern(String pattern) { this.includePattern = pattern; }

    public String getExcludePattern() { return excludePattern; }
    public void setExcludePattern(String pattern) { this.excludePattern = pattern; }

    public boolean isDirsFirst() { return dirsFirst; }
    public void setDirsFirst(boolean dirsFirst) { this.dirsFirst = dirsFirst; }

    public boolean isHumanReadable() { return humanReadable; }
    public void setHumanReadable(boolean readable) { this.humanReadable = readable; }

    public boolean isShowSize() { return showSize; }
    public void setShowSize(boolean show) { this.showSize = show; }
}

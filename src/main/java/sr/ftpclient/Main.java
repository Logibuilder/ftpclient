package sr.ftpclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            // Parser les arguments
            Commande commande = ArgumentParser.parse(args);

            // Créer et configurer le client FTP
            FTPClient client = new FTPClient();
            client.setCommande(commande);

            // Connexion
            client.connect(commande.getHost(), 21);
            client.login(commande.getLogin(), commande.getPassword());

            // Exécuter l'exploration selon le format demandé
            if (commande.getOutputFormat() == Commande.OutputFormat.JSON) {
                // Format JSON
                FTPParser.FileInfo tree = client.treeJSON();

                Gson gson = new GsonBuilder()
                        .excludeFieldsWithoutExposeAnnotation()
                        .setPrettyPrinting()
                        .create();

                String json = gson.toJson(tree);
                System.out.println(json);

                // Optionnel : sauvegarder dans un fichier
                try (FileWriter writer = new FileWriter(commande.getHost() + ".json")) {
                    writer.write(json);
                }
            } else {
                // Format texte
                if (commande.getTraversalMode() == Commande.TraversalMode.DFS) {
                    client.tree();
                } else {
                    client.ls();
                }
            }

            client.disconnect();

        } catch (FTPException e) {
            System.err.println("Erreur FTP: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Erreur réseau: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Erreur: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
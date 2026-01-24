package sr.ftpclient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class FTPClientTest {




    @Mock
    private FTPSocket mockSocket;

    @InjectMocks
    private FTPClient ftpClient;

    @BeforeEach
    void setUp() {

        //initialiser les objets mocks
        MockitoAnnotations.openMocks(this);

        Commande cmd = new Commande();
        cmd.setHost("test.server.com");
        cmd.setLogin("user");
        cmd.setPassword("password");
        ftpClient.setCommande(cmd);
    }



    @Test
    void connectAndLogin() throws IOException {

        //scénario
        when(mockSocket.read()).thenReturn(
                "220 Service ready",      // Réponse à connect()
                "331 User name okay",     // Réponse à USER
                "230 User logged in"      // Réponse à PASS
        );

        // Exécution
        ftpClient.connect("test.server.com", 21);
        ftpClient.login("user", "password");

        // vérification
        verify(mockSocket).connect();

        // verification
        verify(mockSocket).authenticate("user", "password");
    }

    @Test
    void cdSuccess() throws IOException {
        when(mockSocket.read()).thenReturn("250 Directory successfully changed.");

        boolean res = ftpClient.cd("/bon/chemin");

        assertTrue(res,"cd doit retourner true");

        verify(mockSocket).write("CWD /bon/chemin");
    }


    @Test
    void cdFailure() throws IOException {
        when(mockSocket.read()).thenReturn("550 Failed to change directory.");

        boolean res = ftpClient.cd("/mauvais/chemin");

        assertFalse(res,"cd doit retourner true");

        verify(mockSocket).write("CWD /mauvais/chemin");
    }


    @Test
    void ls() throws IOException {

        //Préparer les fausses données du serveur
        String fakeFiles = "fichier1.txt\ndossier1\n";
        ByteArrayInputStream fakeDataStream = new ByteArrayInputStream(fakeFiles.getBytes());

        //si ls tente de créer un socket de données
        try(
            MockedConstruction<Socket> mockedDataSocket = mockConstruction(Socket.class,
                    (mock, context) -> {
                        // Quand ls() voudra lire les données, on lui donne la fausse liste
                        when(mock.getInputStream()).thenReturn(fakeDataStream);
                    })
            ){

            //Scénario de lecture du socket client
            when(mockSocket.read()).thenReturn(
                    "227 Entering Passive Mode (127,0,0,1,195,80)", // Réponse au PASV
                    "150 Opening ASCII mode data connection",       // Réponse après NLST
                    "226 Transfer complete"                         // Réponse finale
            );

            //execution
            ftpClient.ls();

            //tests
            verify(mockSocket).write("PASV");
            verify(mockSocket).write("NLST");

            verify(mockSocket, times(3)).read();

            //si le socket de données est foermé
            verify(mockedDataSocket.constructed().get(0)).close();
        }
    }

    @Test
    void ls_l() throws IOException {
        //Préparer les données
        String fakeDetails = "-rw-r--r-- 1 user group 1234 Jan 01 12:00 fichier.txt\n" +
                "drwxr-xr-x 2 user group 4096 Jan 01 12:01 dossier\n";
        ByteArrayInputStream fakeDataStream = new ByteArrayInputStream(fakeDetails.getBytes());

        // la création du socket de données
        try (MockedConstruction<Socket> mockedDataSocket = mockConstruction(Socket.class,
                (mock, context) -> {
                    when(mock.getInputStream()).thenReturn(fakeDataStream);
                })) {

            // Scénario du socket de commande
            when(mockSocket.read()).thenReturn(
                    "227 Entering Passive Mode (127,0,0,1,195,80)",
                    "150 Opening ASCII mode data connection",
                    "226 Transfer complete"
            );

            // Exécution
            ftpClient.ls_l();

            // Vérifications
            verify(mockSocket).write("PASV");

            // C'est ici que ça change par rapport à ls() : on attend "LIST"
            verify(mockSocket).write("LIST");

            verify(mockSocket, times(3)).read();

            //verifier le contenu du dernière message
            assertEquals("226 Transfer complete", mockSocket.read());
        }
    }

    @Test
    void cwd() throws IOException{
        // Scénario : Le serveur confirme le changement de dossier (250)
        when(mockSocket.read()).thenReturn("250 Directory successfully changed.");

        // Exécution
        boolean result = ftpClient.cwd();

        // Vérifications
        assertTrue(result, "cwd doit retourner true si le serveur répond 250");
        verify(mockSocket).write("CDUP");
    }

    @Test
    void pwd() throws IOException {
        // Scénario : Le serveur renvoie le chemin courant entre guillemets
        when(mockSocket.read()).thenReturn("257 \"/home/chemin/courent\" is current directory.");

        // Exécution
        String path = ftpClient.pwd();

        // Vérifications
        assertEquals("/home/chemin/courent", path, "pwd doit extraire le chemin correct");
        verify(mockSocket).write("PWD");
    }

    @Test
    void getFiles() throws  IOException {
        String fakeFiles = "-rw-r--r-- 1 user group 100 Jan 01 10:00 file1.txt\n" +
                "drwxr-xr-x 2 user group 4096 Jan 01 10:01 dossier1\n";
        ByteArrayInputStream fakeFilesStream = new ByteArrayInputStream(fakeFiles.getBytes());
        try (
            MockedConstruction<Socket> mockFilesSocket = mockConstruction(Socket.class,
                    (mock, context) -> {
                        when(mock.getInputStream()).thenReturn(fakeFilesStream);
                    })
        )
        {
            //scenario
            when(mockSocket.read()).thenReturn(
                    "227 Entering Passive Mode (127,0,0,1,195,80)",
                    "150 Opening ASCII mode data connection",
                    "226 Transfer complete"
            );

            List<FTPParser.FileInfo> files = ftpClient.getFile();

            // Vérifications
            assertNotNull(files);
            assertEquals(2, files.size(), "Il doit y avoir 2 fichiers détectés");

            // Vérification du contenu
            assertEquals("file1.txt", files.get(0).getName());
            assertEquals(FTPParser.TYPE.FILE, files.get(0).getType());

            assertEquals("dossier1", files.get(1).getName());
            assertEquals(FTPParser.TYPE.DIRECTORY, files.get(1).getType());

        }
    }

    @Test
    void disconnect() throws IOException {

        ftpClient.disconnect();

        verify(mockSocket).close();

    }
}
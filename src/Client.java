import java.util.*;
import java.util.Locale.IsoCountryCode;
import java.io.*;
import java.net.*;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.MessageDigest;

import javax.crypto.Cipher;

class ConnectionIndicator
{
    public Boolean boolVal;

    public ConnectionIndicator(boolean b)
    {
        boolVal = b;
    }
}

class SendingThread implements Runnable
{
    Socket sendingSocket;
    DataOutputStream outToServer;
    BufferedReader outToServerAcks;
    BufferedReader inFromUser;
    ConnectionIndicator isConnected;
    String username;
    byte[] privateKey;
    int modeOfOperation;

    public SendingThread(Socket sendingSocketIn, DataOutputStream outToServerIn, BufferedReader outToServerAcksIn, ConnectionIndicator isConnectedIn, String usernameIn, byte[] privateKeyIn, int modeOfOperationIn)
    {
        sendingSocket = sendingSocketIn;
        outToServer = outToServerIn;
        outToServerAcks = outToServerAcksIn;
        inFromUser = new BufferedReader(new InputStreamReader(System.in));       //Reader that reads the messages types by the user.
        isConnected = isConnectedIn;
        username = usernameIn;
        privateKey = privateKeyIn;
        modeOfOperation = modeOfOperationIn;
    }

    public void run()
    {
        while(isConnected.boolVal)
        {
            System.out.print("\nEnter message: ");
            try
            {
                String message = "";
                try
                {
                    message = inFromUser.readLine();
                    
                    // We got a message but server was disconnected
                    if(!isConnected.boolVal)
                    {
                        System.out.println("Couldn't reach server. Please try again.\n");
                        continue;
                    }
                }
                catch(Exception e)
                {
                    // System.out.println(message);
                    System.out.println("Client disconnected! We will miss you...");
                    outToServer.writeBytes("CLOSECONNECTION " + username + "\n\n");
                    isConnected.boolVal = false;
                    break;
                }
                
            
                String[] messageSplit = {};
                try
                {
                    messageSplit = message.split(" ");
                }
                catch(Exception e)
                {
                    System.out.println("Client disconnected! We will miss you...");
                    isConnected.boolVal = false;
                    outToServer.writeBytes("CLOSECONNECTION " + username + "\n\n");
                    break;
                }
                
                if(!checkMessageFormat(message))
                    continue;


                byte[] publicKeyTargetUser = {};
                String targetUser = messageSplit[0].substring(1);
                if(targetUser.equals("Server") && messageSplit[1].equals("Bye"))
                {
                    System.out.println("Client disconnected! We will miss you...");
                    isConnected.boolVal = false;
                    outToServer.writeBytes("CLOSECONNECTION " + username + "\n\n");
                    break;
                }

                if(modeOfOperation == 2)
                {    
                    String publicKeyTargetUserBase64;
                    outToServer.writeBytes("FETCHKEY " + targetUser + "\n\n");
                    String ack = outToServerAcks.readLine();
                    outToServerAcks.readLine();
                    String[] ackSplit = ack.split(" ");
                    boolean err = false;
                    while(!(ackSplit[0].equals("PUBLICKEY") && ackSplit[1].equals("SENT")))
                    {
                        if(ackSplit[0].equals("ERROR"))
                        {
                            err = true;
                            System.out.println("[" + targetUser + "] not registered as user.\n");
                            break;
                        }
                        outToServer.writeBytes("FETCHKEY " + targetUser + "\n\n");
                        ack = outToServerAcks.readLine();
                        ackSplit = ack.split(" "); //check this forever loop
                    }
                    if(err == true)
                    {
                        continue;
                    }
                    publicKeyTargetUserBase64 = ackSplit[2];
                    publicKeyTargetUser =  java.util.Base64.getDecoder().decode(publicKeyTargetUserBase64);
                }
                else if(modeOfOperation == 3)
                {    
                    String publicKeyTargetUserBase64;
                    outToServer.writeBytes("FETCHKEY3 " + targetUser + "\n\n");
                    String ack = outToServerAcks.readLine();
                    outToServerAcks.readLine();
                    String[] ackSplit = ack.split(" ");
                    boolean err = false;
                    while(!(ackSplit[0].equals("PUBLICKEY") && ackSplit[1].equals("SENT")))
                    {
                        if(ackSplit[0].equals("ERROR"))
                        {
                            err = true;
                            System.out.println("[" + targetUser + "] not registered as user.\n");
                            break;
                        }
                        outToServer.writeBytes("FETCHKEY3 " + targetUser + "\n\n");
                        ack = outToServerAcks.readLine();
                        ackSplit = ack.split(" "); //check this forever loop
                    }
                    if(err == true)
                    {
                        continue;
                    }
                    publicKeyTargetUserBase64 = ackSplit[2];
                    publicKeyTargetUser =  java.util.Base64.getDecoder().decode(publicKeyTargetUserBase64);
                }
                String messagePacket = "";
                try
                {
                    messagePacket = this.processMessage(message, publicKeyTargetUser);
                }
                catch(Exception e)
                {
                    // e.printStackTrace();
                    System.out.println("Message couldn't be encrypted");
                    outToServer.writeBytes("ERROR420 COULDNOT BE ENCRYPTED" + "\n\n");
                    continue;
                }
                String targetUsername = message.split(" ")[0].substring(1);

                if(messagePacket == null)
                    continue;
                
                outToServer.writeBytes(messagePacket);  //Sending the message packet to the server.
                String messageSentAck = outToServerAcks.readLine();    //Waiting for the server's acknowledegement.

                if(messageSentAck.contains("SENT " + targetUsername))
                    System.out.println("Message sent!");
                else if(messageSentAck.contains("ERROR 101"))
                    System.out.println("The user is not registered for sending messages");
                else //if(messageSentAck.contains("ERROR 102") || messageSentAck.contains("ERROR 103"))
                    System.out.println("[" + targetUsername + "] not registered as user.\n");
                
                outToServerAcks.readLine();         //Ignoring the extra \n after the ack message
                
            }
            catch(IOException e)
            {
                System.out.println(e);
            }
        }
    }

    //Checks if the message entered by the user is well formed: Returns the message packet if well-formed
    public String processMessage(String message, byte[] publicKeyTargetUser) throws Exception
    {
       String[] messageSplit = message.split(" ");
         
       return this.constructMessage(messageSplit[0].substring(1), message.substring(0 + messageSplit[0].length() + 1), publicKeyTargetUser);
    }
    
    //Checks of the message entered by message is well formatted
    public boolean checkMessageFormat(String message)
    {
        String[] messageSplit = message.split(" ");
        if(message.length() == 0 || message.charAt(0) != '@' || messageSplit.length <= 1 || messageSplit[0].length() <= 1)
         {
             System.out.println("Invalid message format. Please enter again. (@recipient [message]) \n");
             return false;
         }
        return true;
    }
 
    //Generates a well-formatted string from the message and taregt user to be sent to the server
    public String constructMessage(String targetUser, String content, byte[] publicKeyTargetUser) throws Exception
    {
        String message = new String();
        message = "SEND " + targetUser + "\n";
        message = message + "Content-length: " + content.length() + "\n"; //TODO : bytes length or number of chatacters ?
        if(modeOfOperation == 2 || modeOfOperation == 3)
        {
            Encryptor crypto = new Encryptor();
            byte[] contentBytes = content.getBytes();
            byte[] encryptedData = crypto.encrypt(publicKeyTargetUser, contentBytes); //encrypt function
            String encryptedStringBase64 = java.util.Base64.getEncoder().encodeToString(encryptedData);
            if(modeOfOperation == 2)
               message = message + "\n" + encryptedStringBase64 + "\n";
            else
            {
               MessageDigest md = MessageDigest.getInstance("SHA-256");
               byte[] shaBytes = md.digest(encryptedData);
               //String shaBytesBase64 = java.util.Base64.getEncoder().encodeToString(shaBytes); //H = hash(M_dash)
               byte[] encrytedShaBytesBase64 = crypto.encryptFromPrivateKey(privateKey, shaBytes); 
               String encryptedShaStringBase64 = java.util.Base64.getEncoder().encodeToString(encrytedShaBytesBase64);
               message = message + "\n" + encryptedStringBase64 + "\n" + encryptedShaStringBase64 + "\n";
            }
        }
        else
        {
           message = message + "\n" + content + "\n";
        }
        return message;
    }
}


class RecievingThread implements Runnable
{
    Socket recievingSocket;
    BufferedReader inFromServer;
    DataOutputStream inFromServerAcks;
    ConnectionIndicator isConnected;
    byte[] privateKey;
    int modeOfOperation;
    DataOutputStream outToServer;
    BufferedReader outToServerAcks;

    public RecievingThread(Socket recievingSocketIn, BufferedReader inFromServerIn, DataOutputStream inFromServerAcksIn, ConnectionIndicator isConnectedIn, byte[] privateKeyIn, int modeOfOperationIn, DataOutputStream outToServerIn, BufferedReader outToServerAcksIn)
    {
        recievingSocket = recievingSocketIn;
        inFromServer = inFromServerIn;
        inFromServerAcks = inFromServerAcksIn;
        isConnected = isConnectedIn;
        privateKey = privateKeyIn;
        modeOfOperation = modeOfOperationIn;
        outToServer = outToServerIn;
        outToServerAcks = outToServerAcksIn;
    }

    public void run()
    {
        try
        {
            while(isConnected.boolVal)
            {
                //Reading the FORWARD line
                String message = "";
                try
                {
                    message = inFromServer.readLine();
                }
                catch(Exception e)
                {
                    System.out.println("\nConnection to server lost\n");
                    isConnected.boolVal = false;
                    continue;
                }
                
                if(message == null)
                {
                    System.out.println("\nConnection to server lost\n");
                    isConnected.boolVal = false;
                    continue;
                }

                if(message.equals("STOPRECIEVING"))
                {
                    inFromServer.readLine();    //Ignore the extra \n
                    isConnected.boolVal = false;
                    continue;
                }

                if(message.length() <= 8 || !message.substring(0,8).equals("FORWARD "))
                {
                    inFromServerAcks.writeBytes("ERROR 103 Header incomplete\n");
                    continue;
                }
                String senderUsername = message.split("\n")[0].split(" ")[1];

                //Reading the content-length line
                message = inFromServer.readLine();
                if(message.length() <= 16 || !message.substring(0,16).equals("Content-length: "))
                {
                    inFromServerAcks.writeBytes("ERROR 103 Header incomplete\n\n");
                    continue;
                }
                int contentLength = Integer.parseInt(message.split("\n")[0].split(" ")[1]);

                message = inFromServer.readLine();
                
                // char content[] = new char[contentLength];
                // int flag = inFromServer.read(content, 0, contentLength);
                // String contentString = new String(content);
                String contentString = inFromServer.readLine();
                if(modeOfOperation == 2 || modeOfOperation == 3)
                {
                    byte[] encryptedDataBase64 = java.util.Base64.getDecoder().decode(contentString);
                    Encryptor crypto = new Encryptor();
                    byte[] decryptedData;
                    try
                    {
                        decryptedData = crypto.decrypt(privateKey, encryptedDataBase64);
                    }
                    catch(Exception e)
                    {
                        // e.printStackTrace();
                        System.out.println("New message from [" + senderUsername + "] couldn't be decrypted.");
                        continue;
                    }    
                    String finalContent = new String(decryptedData);
                    System.out.print("\nNew message from [" + senderUsername + "] : " + finalContent + "\n\nEnter message: ");
                    if(modeOfOperation == 3)
                    {
                         String digestString = inFromServer.readLine();
                         outToServer.writeBytes("FETCHKEYMODE3 " + senderUsername + "\n\n");
                         String ack = outToServerAcks.readLine();
                         outToServerAcks.readLine();
                         String[] ackSplit = ack.split(" ");
                         while(!(ackSplit[0].equals("PUBLICKEYMODE3") && ackSplit[1].equals("SENT")))
                         {
                             outToServer.writeBytes("FETCHKEYMODE3 " + senderUsername + "\n\n");
                             ack = outToServerAcks.readLine();
                             ackSplit = ack.split(" "); //check this forever loop
                         }
                         String publicKeySenderUserBase64 = ackSplit[2];
                         byte[] publicKeySenderUser =  java.util.Base64.getDecoder().decode(publicKeySenderUserBase64);

                         byte[] encryptedHashBase64 = java.util.Base64.getDecoder().decode(digestString);
                         byte[] decryptedHash;
                         try
                         {
                             decryptedHash = crypto.decryptByPublicKey(publicKeySenderUser, encryptedHashBase64);
                         }
                         catch(Exception e)
                         {
                             // e.printStackTrace();
                             System.out.println("Digest message from [" + senderUsername + "] couldn't be decrypted.");
                             continue;
                         }    
                         String shaBytes2Base64 = java.util.Base64.getEncoder().encodeToString(decryptedHash);

                         MessageDigest md;
                         try
                         {
                            md = MessageDigest.getInstance("SHA-256");
                         }
                         catch(Exception e)
                         {
                            // e.printStackTrace();
                            System.out.println("Sorry! Couldn't get Instance of SHA-256");
                            continue;
                         }
                         byte[] shaBytes1 = md.digest(encryptedDataBase64);
                         String shaBytes1Base64 = java.util.Base64.getEncoder().encodeToString(shaBytes1); //H = hash(M_dash)
                         System.out.println("hash(M_dash): " + shaBytes1Base64);
                         System.out.println("K_pub(H_dash): " + shaBytes2Base64);
                         System.out.print("Integrity maintained: " + shaBytes1Base64.equals(shaBytes2Base64) + "\n\nEnter message: ");
                         
                    }
                }
                else
                {
                    System.out.print("\nNew message from [" + senderUsername + "] : " + contentString + "\n\nEnter message: ");
                }
                inFromServerAcks.writeBytes("RECIEVED " + senderUsername + "\n\n");       
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}


public class Client
{
    static ConnectionIndicator isConnected = new ConnectionIndicator(true);
    
    public static void main(String args[]) throws Exception
    {
        //Command line inputs: <username> <server IP address>

        if(args.length != 3 || !args[2].matches("[1-3]"))
        {
            System.out.println("Invalid number of arguments or mode. Please try again.");
            System.out.println("Correct usage: 'java Client [username] [Server IP] [mode]'");
            return;
        }

        String username = args[0];
        String serverHost = args[1];
        int modeOfOperation = Integer.parseInt(args[2]);
        Socket sendingSocket = null;
        Socket recievingSocket = null;

        try
        {
            sendingSocket = new Socket(serverHost, 1234);
            recievingSocket = new Socket(serverHost, 1234);
        }
        catch(Exception e)
        {
            System.out.println("\nCouldn't connect to a Peek-a-Boo server. Please try again\n");
            return;
        }

        Encryptor crypto = new Encryptor();
        KeyPair generateKeyPair = crypto.generateKeyPair();
        byte[] publicKey = generateKeyPair.getPublic().getEncoded();
        byte[] privateKey = generateKeyPair.getPrivate().getEncoded();
        String publicKeyBase64 = java.util.Base64.getEncoder().encodeToString(publicKey);

        DataOutputStream outToServer = new DataOutputStream(sendingSocket.getOutputStream());   //TCP connection for sending messages
        BufferedReader outToServerAcks = new BufferedReader(new InputStreamReader(sendingSocket.getInputStream()));

        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(recievingSocket.getInputStream()));  //TCP connection for recieving messages
        DataOutputStream inFromServerAcks = new DataOutputStream(recievingSocket.getOutputStream());

        //Registering the username
        boolean registeredAsSender = false;
        boolean registeredAsReciever = false;

        //Registering as sender
        outToServer.writeBytes("REGISTER TOSEND " + username + "\n\n");
        String ack = outToServerAcks.readLine();
        outToServerAcks.readLine();     //Ignoring the extra \n
        if(ack.contains("REGISTERED TOSEND " + username))
        {
            registeredAsSender = true;
        }
        else if(ack.contains("ERROR 100 Malformed username"))
        {
            System.out.println("The username: " + username + " is invalid. Please try again");
            sendingSocket.close();
            recievingSocket.close();
            return;
        }
        else
        {
            System.out.println("An unknown error occured. We will miss you. Please try again.");
            sendingSocket.close();
            recievingSocket.close();
            return;
        }

        //Registering as reciever
        inFromServerAcks.writeBytes("REGISTER TORECV " + username + " PUBLICKEY " + publicKeyBase64 + "\n\n");
        ack = inFromServer.readLine();
        inFromServer.readLine();     //Ignoring the extra \n
        if(ack.contains("REGISTERED TORECV " + username))
        {
            registeredAsReciever = true;
        }
        else if(ack.contains("ERROR 100 Malformed username"))
        {
            System.out.println("The username: " + username + " is invalid. Please register again. (Only alphanumerals. Avoid spaces and spl chars)");
            sendingSocket.close();
            recievingSocket.close();
            return;
        }
        else
        {
            System.out.println("An unknown error occured. We will miss you. Please register again.");
            sendingSocket.close();
            recievingSocket.close();
            return;
        }

        if(registeredAsReciever && registeredAsSender)
        {
            System.out.println("User: " + username + " is registered with " + serverHost);
        }
        //Registration complete


        Thread sender = new Thread(new SendingThread(sendingSocket, outToServer, outToServerAcks, isConnected, username, privateKey, modeOfOperation));
        Thread reciever = new Thread(new RecievingThread(recievingSocket, inFromServer, inFromServerAcks, isConnected, privateKey, modeOfOperation, outToServer, outToServerAcks));
        sender.start();
        reciever.start();
    }

}

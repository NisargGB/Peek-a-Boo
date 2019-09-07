import java.util.*;
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

class SendingThread implements Runnable
{
    Socket sendingSocket;
    DataOutputStream outToServer;
    BufferedReader outToServerAcks;
    BufferedReader inFromUser;
    Boolean isConnected;
    String username;
    int modeOfOperation;

    public SendingThread(Socket sendingSocketIn, DataOutputStream outToServerIn, BufferedReader outToServerAcksIn, Boolean isConnectedIn, String usernameIn, int modeOfOperationIn)
    {
        sendingSocket = sendingSocketIn;
        outToServer = outToServerIn;
        outToServerAcks = outToServerAcksIn;
        try {outToServerAcks.readLine();} catch(IOException e){e.printStackTrace();}
        inFromUser = new BufferedReader(new InputStreamReader(System.in));       //Reader that reads the messages types by the user.
        isConnected = isConnectedIn;
        username = usernameIn;
        modeOfOperation = modeOfOperationIn;
    }

    public void run()
    {
        while(true && isConnected)
        {
            System.out.print("Enter message: ");
            try
            {
                String message = "";
                try
                {
                    message = inFromUser.readLine();
                }
                catch(Exception e)
                {
                    System.out.println(message);
                    System.out.println("Client disconnected! We will miss you...");
                    isConnected = false;
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
                    isConnected = false;
                    outToServer.writeBytes("CLOSECONNECTION " + username + "\n\n");
                    break;
                }
                
                byte[] publicKeyTargetUser = {};
                if(modeOfOperation == 2)
                {
                    String targetUser = messageSplit[0].substring(1);
                    String publicKeyTargetUserBase64;
                    outToServer.writeBytes("FETCHKEY " + targetUser + "\n\n");
                    String ack = outToServerAcks.readLine();
                    outToServerAcks.readLine();
                    String[] ackSplit = ack.split(" ");
                    while(!(ackSplit[0].equals("PUBLICKEY") && ackSplit[1].equals("SENT")))
                    {
                        outToServer.writeBytes("FETCHKEY " + targetUser + "\n\n");
                        ack = outToServerAcks.readLine();
                        ackSplit = ack.split(" "); //check this forever loop
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
                    System.out.println("Sending failed. Please enter message again");
                
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
         if(message.charAt(0) != '@' || messageSplit.length <= 1)
         {
             System.out.println("Invalid message format. Please enter again. (@recipient [message]) \n");
             return null;
         }
         
         return this.constructMessage(messageSplit[0].substring(1), message.substring(0 + messageSplit[0].length() + 1), publicKeyTargetUser);
     }
 
 
     //Generates a well-formatted string from the message and taregt user to be sent to the server
     public String constructMessage(String targetUser, String content, byte[] publicKeyTargetUser) throws Exception
     {
         String message = new String();
         message = "SEND " + targetUser + "\n";
         message = message + "Content-length: " + content.length() + "\n"; //TODO : bytes length or number of chatacters ?
         if(modeOfOperation == 2)
         {
             Encryptor crypto = new Encryptor();
             byte[] contentBytes = content.getBytes();
             byte[] encryptedData = crypto.encrypt(publicKeyTargetUser, contentBytes); //encrypt function
             String encryptedStringBase64 = java.util.Base64.getEncoder().encodeToString(encryptedData);
             message = message + "\n" + encryptedStringBase64 + "\n";
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
    Boolean isConnected;
    byte[] privateKey;
    int modeOfOperation;

    public RecievingThread(Socket recievingSocketIn, BufferedReader inFromServerIn, DataOutputStream inFromServerAcksIn, Boolean isConnectedIn, byte[] privateKeyIn, int modeOfOperationIn)
    {
        recievingSocket = recievingSocketIn;
        inFromServer = inFromServerIn;
        inFromServerAcks = inFromServerAcksIn;
        isConnected = isConnectedIn;
        privateKey = privateKeyIn;
        modeOfOperation = modeOfOperationIn;
    }

    public void run()
    {
        try
        {
            inFromServer.readLine();
            while(true && isConnected)
            {
                //Reading the FORWARD line
                String message = inFromServer.readLine();
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
                if(modeOfOperation == 2)
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
                        System.out.println("New message from " + senderUsername + " couldn't be decrypted.");
                        continue;
                    }    
                    String finalContent = new String(decryptedData);
                    System.out.println("\nNew message from " + senderUsername + " : " + finalContent);
                }
                else
                {
                    System.out.println("\nNew message from " + senderUsername + " : " + contentString);
                }
                inFromServerAcks.writeBytes("RECIEVED " + senderUsername + "\n\n");       
            }
        }
        catch(IOException e)
        {
            System.out.println(e);
        }
    }
}


public class Client
{
    static Boolean isConnected = true;
    
    public static void main(String args[]) throws Exception
    {
        //Command line inputs: <username> <server IP address>

        String username = args[0];
        String serverHost = args[1];
        int modeOfOperation = Integer.parseInt(args[2]);

        Socket sendingSocket = new Socket(serverHost, 1234);             //TODO
        Socket recievingSocket = new Socket(serverHost, 1234);            //TODO

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


        Thread sender = new Thread(new SendingThread(sendingSocket, outToServer, outToServerAcks, isConnected, username, modeOfOperation));
        Thread reciever = new Thread(new RecievingThread(recievingSocket, inFromServer, inFromServerAcks, isConnected, privateKey, modeOfOperation));
        sender.start();
        reciever.start();
    }

}
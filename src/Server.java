import java.util.*;
import javax.lang.model.util.ElementScanner6;
import java.io.*;
import java.net.*;


class ClientThread implements Runnable
{
    boolean recieving;
    boolean sending;
    Socket clientSocket;
    BufferedReader inFromClient;
    DataOutputStream outToClient;
    Hashtable<String,Socket> recieverSocketsMap;
    Hashtable<String,Socket> senderSocketsMap;
    Hashtable<String,String> publicKeysMap;

    public ClientThread(Socket clientSocketIn, Hashtable<String,Socket> recieversIn, Hashtable<String,Socket> sendersIn, Hashtable<String,String> publicKeysIn)
    {
        recieving = false;
        sending = false;
        clientSocket = clientSocketIn;
        recieverSocketsMap = recieversIn;
        senderSocketsMap = sendersIn;
        publicKeysMap = publicKeysIn;
        try
        {
            inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            outToClient = new DataOutputStream(clientSocket.getOutputStream());
        }
        catch(IOException e)
        {
            System.out.println(e);
        }
    }

    @Override
    public void run()
    {
        try
        {
            boolean registeredClient = false;
            boolean disconnected = false;
            int modeInference = 1;
            
            while(!registeredClient)
            {
                String message = inFromClient.readLine();
                String[] messageSplit = message.split(" ");

                if(messageSplit[0].equals("REGISTER") && messageSplit[1].equals("TOSEND") && messageSplit.length==3)
                {
                    String username = "";                       //Extract the username
                    if(messageSplit[2].split("\n").length==1)
                        username = messageSplit[2].split("\n")[0];
                    
                    if(this.isValidUsername(username))          //Only if username is valid
                    {
                        senderSocketsMap.put(username, clientSocket);
                        registeredClient= true;
                        outToClient.writeBytes("REGISTERED TOSEND " + username + "\n\n");
                        inFromClient.readLine();                //Ignoring the extra \n after the registration message

                        while(true)                             //Accepting messages
                        {
                            if(disconnected == true)
                            {
                                System.out.println("\nThe client " + username + " was disconnected");
                                System.out.println("\nOLD senderSocketsMap was: " + senderSocketsMap);
                                System.out.println("\nOLD recieverSocketsMap was: " + recieverSocketsMap);
                                DataOutputStream ds = new DataOutputStream(recieverSocketsMap.get(username).getOutputStream());
                                ds.writeBytes("STOPRECIEVING\n\n");
                                senderSocketsMap.remove(username);
                                recieverSocketsMap.remove(username);
                                publicKeysMap.remove(username);
                                System.out.println("\nNEW senderSocketsMap is: " + senderSocketsMap);
                                System.out.println("\nNEW recieverSocketsMap is: " + recieverSocketsMap + "\n");
                                break;
                            }

                            String newMessage = "";
                            try
                            {
                                newMessage = inFromClient.readLine();
                            }
                            catch(SocketException se)
                            {
                                System.out.println("\nThe client " + username + " was disconnected");
                                System.out.println("\nOLD senderSocketsMap was: " + senderSocketsMap);
                                System.out.println("\nOLD recieverSocketsMap was: " + recieverSocketsMap);
                                senderSocketsMap.remove(username);
                                recieverSocketsMap.remove(username);
                                publicKeysMap.remove(username);
                                System.out.println("\nNEW senderSocketsMap is: " + senderSocketsMap);
                                System.out.println("\nNEW recieverSocketsMap is: " + recieverSocketsMap + "\n");
                                break;
                            }

                            String targetUser = "";
                            try
                            {
                                targetUser = newMessage.split(" ")[1].split("\n")[0];//FETCHKEY + targetuser
                            }
                            catch(Exception e)
                            {
                                System.out.println("\nThe client " + username + " was disconnected");
                                System.out.println("\nOLD senderSocketsMap was: " + senderSocketsMap);
                                System.out.println("\nOLD recieverSocketsMap was: " + recieverSocketsMap);
                                senderSocketsMap.remove(username);
                                recieverSocketsMap.remove(username);
                                publicKeysMap.remove(username);
                                System.out.println("\nNEW senderSocketsMap is: " + senderSocketsMap);
                                System.out.println("\nNEW recieverSocketsMap is: " + recieverSocketsMap + "\n");
                                break;
                            }

                            if((newMessage.split(" ")[0]).equals("CLOSECONNECTION"))
                            {
                                inFromClient.readLine(); //ignoring extra \n
                                disconnected = true;
                                continue;
                            }
                            if(newMessage.split(" ")[0].equals("FETCHKEYMODE3"))
                            {
                                outToClient.writeBytes("PUBLICKEYMODE3 SENT " + publicKeysMap.get(targetUser) + "\n\n");
                                inFromClient.readLine();    //ignoring extra \n
                                continue;
                            }
                            if((newMessage.split(" ")[0]).equals("FETCHKEY"))   //this is encrypted mode of conversation for mode == 2
                            {                            
                                
                                inFromClient.readLine();                //Ignoring the extra \n
                                if(publicKeysMap.get(targetUser) == null)
                                {
                                    outToClient.writeBytes("ERROR 102 Unable to Send" + "\n\n");
                                    // System.out.println(targetUser + " not registered.");
                                    continue;
                                }
                                outToClient.writeBytes("PUBLICKEY SENT " + publicKeysMap.get(targetUser) + "\n\n");
                                newMessage = inFromClient.readLine();//SEND targetuser
                                modeInference = 2;
                            }
                            if((newMessage.split(" ")[0]).equals("FETCHKEY3"))   //this is encrypted mode of conversation for mode == 3
                            {                            
                                inFromClient.readLine();
                                if(publicKeysMap.get(targetUser) == null)
                                {
                                    outToClient.writeBytes("ERROR 102 Unable to Send" + "\n\n");
                                    // System.out.println(targetUser + " not registered.");
                                    continue;
                                }
                                outToClient.writeBytes("PUBLICKEY SENT " + publicKeysMap.get(targetUser) + "\n\n");
                                newMessage = inFromClient.readLine();//SEND targetuser
                                modeInference = 3;
                            }
                            if(newMessage.split(" ")[0].equals("ERROR420") && newMessage.split(" ")[1].equals("COULDNOT") && newMessage.split(" ")[2].equals("BE") && newMessage.split(" ")[3].equals("ENCRYPTED"))
                            {
                                inFromClient.readLine();        //Ignoring the extra \n
                                continue;
                            }
                            newMessage = inFromClient.readLine();
                            
                            inFromClient.readLine();                    //Ignoring the extra \n
                            
                            int contentLength = Integer.parseInt(newMessage.split(" ")[1]);
                            // char[] content = new char[contentLength];
                            // int flag = inFromClient.read(content, 0, contentLength);
                            // String contentString = new String(content);
                            String contentString = inFromClient.readLine();
                            String packetToBeSent;
                            if(modeInference == 3)
                            {
                                String hashString = inFromClient.readLine(); //only for mode == 3
                                System.out.println(contentString);
                                packetToBeSent =  "FORWARD " + username + "\n" + newMessage + "\n\n" + contentString + "\n" + hashString + "\n";
                            }
                            else 
                            {
                                System.out.println(contentString);
                                packetToBeSent =  "FORWARD " + username + "\n" + newMessage + "\n\n" + contentString + "\n";
                            }
                            Socket targetSocket = recieverSocketsMap.get(targetUser);
                            if(targetSocket==null)
                            {
                                outToClient.writeBytes("ERROR 102 Unable to send\n\n");
                                // System.out.println(targetUser + "not registered.");
                                continue;
                            }

                            DataOutputStream targetStream = new DataOutputStream(targetSocket.getOutputStream());
                            targetStream.writeBytes(packetToBeSent);

                            outToClient.writeBytes("SENT " + targetUser + "\n\n");
                        }
                    }
                    else
                    {
                        this.sendUsernameError();
                        System.out.println("here");
                        disconnected = true;
                    }
                }
                else if(messageSplit[0].equals("REGISTER") && messageSplit[1].equals("TORECV") && messageSplit[3].equals("PUBLICKEY") && messageSplit.length==5)
                {
                    String username = "";
                    String publicKeyBase64;
                    // if(messageSplit[2].split("\n").length==1)
                        username = messageSplit[2].split("\n")[0];
                        publicKeyBase64 = messageSplit[4];
                        publicKeysMap.put(username, publicKeyBase64);
                    
                    inFromClient.readLine();    //Ignoring the extra \n after REGISTER line
                    recieverSocketsMap.put(username, clientSocket);
                    registeredClient = true;
                    outToClient.writeBytes("REGISTERED TORECV " + username + "\n\n");
                }
                else if(messageSplit.length==5)
                {
                    outToClient.writeBytes("ERROR 101 No user registered \n\n");
                }
                else
                {
                    this.sendUsernameError();
                    break;
                }
            }
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }


    public boolean isValidUsername(String username)
    {
        return username.matches("[a-zA-Z0-9]+") && !(recieverSocketsMap.containsKey(username) || senderSocketsMap.containsKey(username));
    }

    public void sendUsernameError() throws IOException
    {
        outToClient.writeBytes("ERROR 100 Malformed username\n\n");
    }
}



public class Server
{
    static Hashtable<String,Socket> recieverSockets = new Hashtable<String,Socket>();
    static Hashtable<String,Socket> senderSockets = new Hashtable<String,Socket>();
    static Hashtable<String,String> publicKeys = new Hashtable<String,String>();

    public static void main(String args[]) throws Exception
    {
        System.out.println("\n----------------------Server started----------------------\n");

        ServerSocket serverSocket = new ServerSocket(1234);
        ArrayList<Thread> threads = new ArrayList<Thread>();

        while(true)
        {
            Socket connection = serverSocket.accept();
            Thread t = new Thread(new ClientThread(connection, recieverSockets, senderSockets, publicKeys));
            threads.add(t);
            t.start();
        }
    }

}

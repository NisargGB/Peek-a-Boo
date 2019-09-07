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
                                System.out.println("The client " + username + " was disconnected");
                                System.out.println("earlier senderSocketsMap was: " + senderSocketsMap);
                                System.out.println("earlier recieverSocketsMap was: " + recieverSocketsMap);
                                senderSocketsMap.remove(username);
                                recieverSocketsMap.remove(username);
                                publicKeysMap.remove(username);
                                System.out.println("updated senderSocketsMap is: " + senderSocketsMap);
                                System.out.println("updated senderSocketsMap is: " + recieverSocketsMap);
                                break;
                            }

                            String newMessage = "";
                            try
                            {
                                newMessage = inFromClient.readLine();
                            }
                            catch(SocketException se)
                            {
                                System.out.println("The client " + username + " was disconnected");
                                System.out.println("earlier senderSocketsMap was: " + senderSocketsMap);
                                System.out.println("earlier recieverSocketsMap was: " + recieverSocketsMap);
                                senderSocketsMap.remove(username);
                                recieverSocketsMap.remove(username);
                                publicKeysMap.remove(username);
                                System.out.println("updated senderSocketsMap is: " + senderSocketsMap);
                                System.out.println("updated senderSocketsMap is: " + recieverSocketsMap);
                                break;
                            }


                            String targetUser = newMessage.split(" ")[1].split("\n")[0];//FETCHKEY + targetuser

                            if((newMessage.split(" ")[0]).equals("CLOSECONNECTION"))
                            {
                                inFromClient.readLine(); //ignoring extra \n
                                disconnected = true;
                                continue;
                            }
                            if((newMessage.split(" ")[0]).equals("FETCHKEY"))   //this is encrypted mode of conversation
                            {                            
                                outToClient.writeBytes("PUBLICKEY SENT " + publicKeysMap.get(targetUser) + "\n\n");

                                inFromClient.readLine();
                                inFromClient.readLine();//SEND targetuser
                            }
                            newMessage = inFromClient.readLine();
                            inFromClient.readLine();                    //Ignoring the extra \n
                            
                            int contentLength = Integer.parseInt(newMessage.split(" ")[1]);
                            // char[] content = new char[contentLength];
                            // int flag = inFromClient.read(content, 0, contentLength);
                            // String contentString = new String(content);
                            String contentString = inFromClient.readLine();
                            System.out.println(contentString);
                            String packetToBeSent =  "FORWARD " + username + "\n" + newMessage + "\n\n" + contentString + "\n";
                            
                            Socket targetSocket = recieverSocketsMap.get(targetUser);
                            if(targetSocket==null)
                            {
                                outToClient.writeBytes("ERROR 102 Unable to send\n");
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
        return username.matches("[a-zA-Z0-9]+");
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
        //Command line inputs:

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
package ru.spbau.mit;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Created by Oleg on 5/31/2017.
 */
public class InstantMessenger {

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 3) {
            ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]), args[2]);
            client.start();
            return;
        }

        if (args.length == 1) {
            io.grpc.Server server = ServerBuilder.forPort(Integer.parseInt(args[0])).addService(new ChatServer()).build();
            server.start();
            server.awaitTermination();
            return;
        }

        throw new IllegalArgumentException("Invalid count arguments");
    }

}

class ChatClient {
    private static final Logger logger = Logger.getLogger(ChatClient.class.getName());
    private static final DateTimeFormatter FORMAT_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private String name;
    private String host;
    private int port;
    private StreamObserver<Protocol.ProtocolMessage> chat;

    ChatClient(String host, int port, String name) {
        this.host = host;
        this.port = port;
        this.name = name;
    }

    private static String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return FORMAT_TIME.format(now);
    }

    private void send(final String msg) throws InterruptedException {
        chat.onNext(Protocol.ProtocolMessage.newBuilder()
                .setName(name)
                .setDate(getCurrentDate())
                .setMessage(msg).build());
    }

    void start() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext(true)
                .build();
        ChatServiceGrpc.ChatServiceStub chatService = ChatServiceGrpc.newStub(channel);
        chat = chatService.chat(new StreamObserver<Protocol.ServerMessage>() {
            @Override
            public void onNext(Protocol.ServerMessage value) {
                String response = value.getMessage().getDate() + " "
                        + value.getMessage().getName() + ":"
                        + value.getMessage().getMessage();
                System.out.println(response);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("Error");
                System.exit(1);
            }

            @Override
            public void onCompleted() {
                System.out.println("Completed");
                System.exit(1);
            }
        });

        Scanner in = new Scanner(System.in);
        while (true) {
            String message = in.nextLine();
            send(message);
            logger.info(name + ": " + message);
        }
    }
}

class ChatServer extends ChatServiceGrpc.ChatServiceImplBase {
    private static final Logger logger = Logger.getLogger(ChatServer.class.getName());

    private static LinkedHashSet<StreamObserver<Protocol.ServerMessage>> observers = new LinkedHashSet<>();

    @Override
    public StreamObserver<Protocol.ProtocolMessage> chat(final StreamObserver<Protocol.ServerMessage> responseObserver) {
        observers.add(responseObserver);

        return new StreamObserver<Protocol.ProtocolMessage>() {
            @Override
            public void onNext(Protocol.ProtocolMessage value) {
                logger.info(value.getName() + ": " + value.getMessage());
                Protocol.ServerMessage message = Protocol.ServerMessage
                        .newBuilder()
                        .setMessage(value)
                        .build();
                observers.stream().forEach(observer -> observer.onNext(message));
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                observers.remove(responseObserver);
            }
        };
    }
}



package org.dsa.iot.amqp.server;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.dsa.iot.amqp.AmqpHandler;
import org.dsa.iot.dslink.node.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

public class AmqpRemoteController {
    private static final Logger LOG = LoggerFactory.getLogger(AmqpRemoteController.class);

    private AmqpHandler handler;
    private AmqpRemoteConfig config;
    private Channel channel;
    private ArrayList<RequestHandler> requestHandlers;
    private ServerInputRequestHandler inputRequestHandler;
    private Node node;

    public AmqpRemoteController(AmqpHandler handler, AmqpRemoteConfig config, Node node) {
        this.handler = handler;
        this.config = config;
        this.requestHandlers = new ArrayList<>();
        this.node = node;

        node.setMetaData(this);
    }

    public AmqpHandler getHandler() {
        return handler;
    }

    public void init() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setUri(config.getUrl());
        Connection conn = factory.newConnection();
        this.channel = conn.createChannel();

        inputRequestHandler = new ServerInputRequestHandler(this);

        String inputRequestQueueName = getBrokerPathPrefix("input.request");
        channel.queueDeclare(inputRequestQueueName, true, false, true, null);
        channel.basicConsume(inputRequestQueueName, inputRequestHandler);
    }

    public String getBrokerPathPrefix(String path) {
        return "broker." + config.getBrokerId() + "." + path;
    }

    public Channel getChannel() {
        return channel;
    }

    public void addRequestHandler(RequestHandler handler, String receiverQueue) {
        if (!requestHandlers.contains(handler)) {
            requestHandlers.add(handler);
            handler.onListenerAdded();
        } else {
            handler = findEquivalentHandler(handler);

            if (handler != null) {
                handler.onListenerAdded();

                if (receiverQueue != null && handler instanceof HandlesInitialState) {
                    ((HandlesInitialState) handler).handleInitialState(receiverQueue);
                }
            }
        }
    }

    public void destroy() {
        for (RequestHandler handler : requestHandlers) {
            handler.destroy();
        }

        requestHandlers.clear();

        if (channel != null) {
            try {
                channel.close();
            } catch (IOException | TimeoutException e) {
                LOG.warn("Error while closing AMQP channel.", e);
            }
            channel = null;
        }
    }

    public ServerInputRequestHandler getInputRequestHandler() {
        return inputRequestHandler;
    }

    public Node getNode() {
        return node;
    }

    public void removeRequestHandler(RequestHandler handler) {
        requestHandlers.remove(handler);
    }

    @SuppressWarnings("unchecked")
    public <T> T findEquivalentHandler(T handler) {
        for (RequestHandler r : requestHandlers) {
            if (handler.equals(r)) {
                return (T) r;
            }
        }
        return null;
    }
}

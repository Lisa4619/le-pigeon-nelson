package com.jmtrivial.lepigeonnelson.broadcastplayer;

import android.app.Activity;
import android.location.Location;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.JsonReader;
import android.util.Log;

import com.jmtrivial.lepigeonnelson.broadcastplayer.messages.BMessage;
import com.jmtrivial.lepigeonnelson.broadcastplayer.messages.ConditionFactory;
import com.jmtrivial.lepigeonnelson.broadcastplayer.messages.MessageCondition;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;


public class MessageCollector extends Handler {
    public static final int startCollect = 0;
    public static final int stopCollect = 1;
    public static final int processCollect = 2;
    public static final int getDescription = 3;

    private final SensorsService sensorManager;

    private MessageQueue msgQueue;

    private ConditionFactory cFactory;

    private ArrayList<BMessage> newMessages;

    private ServerDescription currentServer;
    private int serverID;
    private boolean running;
    private String deviceID;


    private UIHandler uiHandler;

    public MessageCollector(MessageQueue msg, Activity activity, UIHandler uiHandler) {

        sensorManager = SensorsService.getSensorsService(activity);
        cFactory = new ConditionFactory();
        this.newMessages = new ArrayList<>();
        this.msgQueue = msg;
        running = false;
        serverID = 0;

        this.uiHandler = uiHandler;

        this.deviceID = Settings.Secure.getString(activity.getContentResolver(),
                Settings.Secure.ANDROID_ID);
    }

    @Override
    public final void handleMessage(Message msg) {
        if (msg.what == getDescription) {
            ServerDescription description = (ServerDescription) msg.obj;
            collect(description, true);
        }
        else if (msg.what == stopCollect) {
            Log.d("MessageCollector", "stop collect");
            running = false;
        }

        else if (msg.what == startCollect) {
            Log.d("MessageCollector", "start collect");
            this.currentServer = (ServerDescription) msg.obj;
            serverID += 1;
            running = true;

            collectMessages(0);
        }
        else if (msg.what == processCollect) {
            Integer id = (Integer) msg.obj;
            // only run this process if it corresponds to the current server
            if (running && id == serverID) {
                Date d = new Date();
                long releaseTime = d.getTime() + currentServer.getPeriodMilliseconds();
                Log.d("MessageCollector", "get data from server");

                // collect messages from the server
                if (collect(currentServer, false)) {
                    // send them to the message queue
                    Message msgQ = msgQueue.obtainMessage();
                    msgQ.obj = newMessages;
                    msgQ.what = msgQueue.addNewMessages;
                    msgQueue.sendMessage(msgQ);

                    if (currentServer.getPeriodMilliseconds() != 0) {
                        // wait the desired period before collecting again, only if it's asked
                        Date d2 = new Date();
                        long time = releaseTime - d2.getTime();
                        collectMessages(time);
                    }
                }
            }
        }
    }

    private void collectMessages(long d) {
        Message msg = obtainMessage();
        msg.obj = serverID;
        msg.what = processCollect;
        if (d <= 0) {
            sendMessage(msg);
        }
        else {
            sendMessageDelayed(msg, d);
        }
    }

    private boolean collect(ServerDescription serverDescription, boolean description) {

        URL url = null;
        try {
            if (description)
                url = new URL(serverDescription.getUrl() + "?self-description");
            else
                url = new URL(serverDescription.getUrl() + getURLParameters());
        } catch (MalformedURLException e) {
            e.printStackTrace();
            uiHandler.sendEmptyMessage(uiHandler.SERVER_ERROR);
            return false;
        }
        catch (Exception e) {
            uiHandler.sendEmptyMessage(uiHandler.NO_GPS);
            return false;
        }

        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();

        } catch (IOException e) {
            e.printStackTrace();
            uiHandler.sendEmptyMessage(uiHandler.SERVER_ERROR);
            return false;
        }

        JsonReader reader = null;
        try {
            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            reader = new JsonReader(new InputStreamReader(in, serverDescription.getEncoding()));

            if (description) {
                readDescription(reader, serverDescription);
            }
            else {
                readMessagesArray(reader);
            }

        } catch (IOException | NumberFormatException e) {
            e.printStackTrace();
            uiHandler.sendEmptyMessage(uiHandler.SERVER_CONTENT_ERROR);
            return false;
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
        }

        try {
            if (reader != null)
                reader.close();
        } catch (IOException e) {
            e.printStackTrace();
            uiHandler.sendEmptyMessage(uiHandler.SERVER_ERROR);
            return false;
        }

        return true;
    }

    private void readDescription(JsonReader reader, ServerDescription serverDescription) throws IOException {
        String name = null;
        String description = null;
        String encoding = null;
        Integer defaultPeriod = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String jname = reader.nextName();
            if (jname.equals("name")) {
                name = reader.nextString();
            }
            else if (jname.equals("description")) {
                description = reader.nextString();
            }
            else if (jname.equals("encoding")) {
                encoding = reader.nextString();
            }
            else if (jname.equals("defaultPeriod")) {
                defaultPeriod = reader.nextInt();
            }
            else {
                reader.skipValue();
            }
        }
        reader.endObject();

        if (name != null && description != null && encoding != null && defaultPeriod != null) {
            ServerDescription newDescription = new ServerDescription(serverDescription.getUrl());
            newDescription.setName(name).setDescription(description)
                    .setEncoding(encoding).setPeriod(defaultPeriod)
                    .setIsEditable(true).setIsSelfDescribed(true);

            Message msg = uiHandler.obtainMessage();
            msg.obj = newDescription;
            msg.what = uiHandler.NEW_SERVER_DESCRIPTION;
            uiHandler.sendMessage(msg);
        }



    }


    private String getURLParameters() throws Exception {
        Location location = sensorManager.getLocation();
        float azimuth = sensorManager.getAzimuth();
        float pitch = sensorManager.getPitch();
        float roll = sensorManager.getRoll();
        if (location != null) {
            URLParamBuilder params = new URLParamBuilder();
            params.addParameter("lat", location.getLatitude());
            params.addParameter("lng", location.getLongitude());
            params.addParameter("azimuth", azimuth);
            params.addParameter("pitch", pitch);
            params.addParameter("roll", roll);

            params.addParameter("uid", deviceID);

            return params.toString();
        }
        else {
            throw new Exception();
        }
    }

    private void readMessagesArray(JsonReader reader) throws IOException {
        newMessages.clear();
        Date d = new Date();
        long ctime = d.getTime();
        int i = 0;

        reader.beginArray();
        while (reader.hasNext()) {
            BMessage msg = readMessage(reader);
            if (msg != null) {
                msg.setCollectedTimestamp(ctime);
                msg.setLocalID(i);
                i += 1;
                newMessages.add(msg);
            }
            else
                throw new IOException();
        }
        reader.endArray();

    }

    private BMessage readMessage(JsonReader reader) throws IOException {
        String txt = null;
        int priority = 10;
        String lang = null;
        String audioURL = null;

        ArrayList<MessageCondition> required = new ArrayList<>();
        ArrayList<MessageCondition> forgettingConditions = new ArrayList<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("txt")) {
                txt = reader.nextString();
            }
            else if (name.equals("lang")) {
                lang = reader.nextString();
            }
            else if (name.equals("priority")) {
                priority = reader.nextInt();
            }
            else if (name.equals("audioURL")) {
                audioURL = reader.nextString();
            }
            else if (name.equals("requiredConditions")) {
                required = readConditions(reader);
            }
            else if (name.equals("forgettingConditions")) {
                forgettingConditions = readConditions(reader);
            }
            else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return new BMessage(txt, lang, audioURL, priority, required, forgettingConditions);

    }

    private ArrayList<MessageCondition> readConditions(JsonReader reader) throws IOException {
        ArrayList<MessageCondition> result = new ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            MessageCondition c = readCondition(reader);
            if (c != null) {
                result.add(c);
            }
            else
                throw new IOException();

        }
        reader.endArray();
        return result;
    }

    private MessageCondition readCondition(JsonReader reader) throws IOException {
        String ref = null;
        String comparison = null;
        String parameter = null;
        boolean reverse = false;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (name.equals("reference")) {
                if (parameter != null)
                    reverse = true;
                ref = reader.nextString();
            }
            else if (name.equals("comparison")) {
                comparison = reader.nextString();
            }
            else if (name.equals("parameter")) {
                parameter = reader.nextString();
            }
            else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return cFactory.getCondition(ref, comparison, parameter, reverse);
    }


    public void setCurrentServer(ServerDescription currentServer) {
        this.currentServer = currentServer;
    }


}

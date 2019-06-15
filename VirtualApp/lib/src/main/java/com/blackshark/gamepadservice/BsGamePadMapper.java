package com.blackshark.gamepadservice;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.ArrayMap;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Xml;

import com.lody.virtual.helper.utils.FastXmlSerializer;
import com.lody.virtual.helper.utils.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Created by max.ma on 2017/10/19.
 */

public class BsGamePadMapper {
    private static final String TAG = "BsGamePadMapper";
    final AtomicFile mFile;
    private final ArrayMap<Integer, ButtonPadMap> mGamePadMapper = new ArrayMap<>();
    private Handler mHandler = new Handler();
    final Runnable mWriteRunner = new Runnable() {
        public void run() {
            synchronized(BsGamePadMapper.this){
                AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                    @Override protected Void doInBackground(Void... params) {
                        writeXml();
                        return null;
                    }
                };
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void[])null);
            }
        }
    };

    BsGamePadMapper(File storagePath){
        mFile = new AtomicFile(storagePath);
        initXml();
    }

    public ArrayMap<Integer, ButtonPadMap> getGamePadMapper(){
        return mGamePadMapper;
    }

    public void updateGamePadMapper(int touchMask, int x, int y, int r){
        synchronized (mGamePadMapper){
            int index = mGamePadMapper.indexOfKey(touchMask);
            if (index >= 0){
                mGamePadMapper.setValueAt(index, new ButtonPadMap(x, y, r));
            } else {
                mGamePadMapper.put(touchMask, new ButtonPadMap(x, y, r));
            }
        }

        mHandler.post(mWriteRunner);
    }

    public void syncGamePadMapper(){
        mHandler.post(mWriteRunner);
    }

    private void writeXml(){
        synchronized(mFile){
            FileOutputStream stream;
            try {
                stream = mFile.startWrite();
            } catch (IOException e) {
                Log.w(TAG, "Failed to write state: " + e);
                return;
            }

            try {
                XmlSerializer out = new FastXmlSerializer();
                out.setOutput(stream, StandardCharsets.UTF_8.name());
                out.startDocument(null, true);
                out.startTag(null, "zsgamepadmapper");

                synchronized(mGamePadMapper){
                    for (int i=0;i<mGamePadMapper.size();i++){
                        boolean isDirectionPad = false;
                        if (mGamePadMapper.valueAt(i).radius != 0){
                            out.startTag(null, "directionpad");
                            isDirectionPad = true;
                        } else {
                            out.startTag(null, "button");
                        }

                        out.attribute(null, "touchMask", Integer.toString(mGamePadMapper.keyAt(i)));
                        out.attribute(null, "centerX", Float.toString(mGamePadMapper.valueAt(i).centerX));
                        out.attribute(null, "centerY", Float.toString(mGamePadMapper.valueAt(i).centerY));

                        if (isDirectionPad) {
                            out.attribute(null, "radius", Float.toString(mGamePadMapper.valueAt(i).radius));
                            out.endTag(null, "directionpad");
                        } else {
                            out.endTag(null, "button");
                        }
                    }
                }

                out.endTag(null, "zsgamepadmapper");
                out.endDocument();
                mFile.finishWrite(stream);
            } catch (IOException e) {
                Log.w(TAG, "Failed to write state, restoring backup.", e);
                mFile.failWrite(stream);
            }
        }
    }

    private void initXml(){
        synchronized(mFile){
            FileInputStream stream;
            try {
                stream = mFile.openRead();
            } catch (FileNotFoundException e) {
                return;
            }

            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(stream, StandardCharsets.UTF_8.name());

                int type;
                while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                    ;
                }

                if (type != XmlPullParser.START_TAG) {
                    throw new IllegalStateException("no start tag found");
                }

                int outerDepth = parser.getDepth();
                while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                    if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                        continue;
                    }

                    String tagName = parser.getName();
                    if (tagName.equals("directionpad") || tagName.equals("button")) {
                        int touchid = Integer.parseInt(parser.getAttributeValue(null, "touchMask"));
                        float centerX = Float.parseFloat(parser.getAttributeValue(null, "centerX"));
                        float centerY = Float.parseFloat(parser.getAttributeValue(null, "centerY"));
                        float radius = 0;
                        if (tagName.equals("directionpad")) {
                            radius = Float.parseFloat(parser.getAttributeValue(null, "radius"));
                        }
                        ButtonPadMap map = new ButtonPadMap(centerX, centerY, radius);
                        mGamePadMapper.put(touchid, map);
                    } else {
                        Log.w(TAG, "Unknown element under <app-ops>: "
                            + parser.getName());
                        XmlUtils.skipCurrentTag(parser);
                    }
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (NullPointerException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (XmlPullParserException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (IOException e) {
                Log.w(TAG, "Failed parsing " + e);
            } catch (IndexOutOfBoundsException e) {
                Log.w(TAG, "Failed parsing " + e);
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                }
            }
        }
    }

    public static final class ButtonPadMap {
        private float centerX;
        private float centerY;
        private float radius;

        public ButtonPadMap(float x, float y, float radius){
            this.centerX = x;
            this.centerY = y;
            this.radius = radius;
        }

        public float getCenterX() {
            return centerX;
        }

        public float getCenterY() {
            return centerY;
        }

        public float getRadius() {
            return radius;
        }

        public String toString() {
            return String.format("Pos {x = %f, y = %f, r = %f}", centerX, centerY, radius);
        }
    }
}

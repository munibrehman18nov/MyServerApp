package com.example.myserverapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.NonNull;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import Model.Driver;

public class MyReceiver extends BroadcastReceiver
{
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG = "SmsBroadcastReceiver";
    private String msg, phoneNo = "";
    private ArrayList<Driver> aDList;
    int radius = 30;

    @Override
    public void onReceive(final Context context, Intent intent)
    {
        aDList = new ArrayList();
        //loadAllAvailableDrivers();
        Log.i(TAG, "Intent Received: "+intent.getAction());
        if(intent.getAction() == SMS_RECEIVED)
        {
            Bundle dataBundle = intent.getExtras();
            if(dataBundle!=null)
            {
                // creating a PDU (Protocol Data Unit) object which is a protocol for transferring message
                Object[] myPdu = (Object[])dataBundle.get("pdus");
                final SmsMessage[] message = new SmsMessage[myPdu.length];

                for(int i=0; i<myPdu.length; i++)
                {
                    // for build version >= API level 23
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    {
                        String format = dataBundle.getString("format");
                        // from PDU, we get all object and SmsMessage object using following code
                        message[i] = SmsMessage.createFromPdu((byte[])myPdu[i], format);

                    }
                    else
                    {
                        message[i] = SmsMessage.createFromPdu((byte[])myPdu[i]);
                    }

                    msg = message[i].getMessageBody();
                    phoneNo = message[i].getOriginatingAddress();
                }

                //System.out.println("MYANS: ."+msg.substring(0,2)+".");
                if(msg.substring(0,3).equals("C1C"))
                {
                    String token[] = msg.split("#_#");
                    double lat = Double.parseDouble(token[0].substring(3).trim());
                    double lng = Double.parseDouble(token[1].trim());
                    final String id = token[2].trim();
                    System.out.println("id: "+id+"\nLat: "+lat+"\nLong: "+lng);
                    Toast.makeText(context, "id: "+id+"\nLat: "+lat+"\nLong: "+lng, Toast.LENGTH_LONG).show();

                    DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child("CustomerRequests");
                    databaseReference.child(id).child("BookedOffline").setValue(true);
                    databaseReference.child(id).child("sendMessageNo").setValue(phoneNo);
                    databaseReference.child(id).child("lat").setValue(lat);
                    databaseReference.child(id).child("lng").setValue(lng);

                    DatabaseReference databaseReference1 = FirebaseDatabase.getInstance().getReference().child("DriversAvailable");
                    GeoFire geoFire = new GeoFire(databaseReference1);
                    GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(lat, lng), radius);
                    geoQuery.removeAllListeners();

                    geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
                        @Override
                        public void onKeyEntered(String key, GeoLocation location) {
                            FirebaseDatabase.getInstance().getReference().child("DriversAvailable").child(key).removeValue();
                            DatabaseReference dr = FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers");
                            dr = dr.child(key);
                            dr.child("UserID").setValue(id);
                            dr.child("isDriverConfirmed").setValue(false);
                            final String id = key;
                            dr = FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers");
                            dr.child(key).addValueEventListener(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if (dataSnapshot.hasChild("isDriverConfirmed")) {
                                        if (dataSnapshot.child("isDriverConfirmed").getValue().equals(true))
                                        {
                                            sendDataToPassenger(id, phoneNo, context);
                                            FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers").child(id).removeValue();
                                        }
                                        else {
                                            FirebaseDatabase.getInstance().getReference().child("ShowingRequestedDrivers").child(id).removeValue();
                                        }
                                    }
                                }

                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {

                                }
                            });
                            final Timer t = new Timer();
                            t.schedule(new TimerTask() {
                                public void run() {
                                    // when the task active then close the dialog
                                    t.cancel(); // also just top the timer thread, otherwise, you may receive a crash report
                                }
                            }, 15000);
                        }


                        @Override
                        public void onKeyExited(String key) {
                        }

                        @Override
                        public void onKeyMoved(String key, GeoLocation location) {
                        }

                        @Override
                        public void onGeoQueryReady() {
                        }

                        @Override
                        public void onGeoQueryError(DatabaseError error) {
                            //  Snackbar.make(,"Internet Error...",5000);
                        }
                    });
                }
            }
        }
    }

    private void sendDataToPassenger(String driver_id, final String mobileToSend, final Context context)
    {
        DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driver_id);

        databaseReference.addValueEventListener(new ValueEventListener()
        {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot)
            {
                String name = dataSnapshot.child("name").getValue().toString().trim();
                String mobile = dataSnapshot.child("mobile").getValue().toString().trim();
                String messageToSend = "Driver Name: " + name + "\n Contact: " + mobile;
                ArrayList<String> msgStringArray = SmsManager.getDefault().divideMessage(messageToSend);
                SimUtil.sendMultipartTextSMS(context, 0, mobileToSend, null, msgStringArray, null, null);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    /*private void loadAllAvailableDrivers()
    {
        DatabaseReference availableDrivers = FirebaseDatabase.getInstance().getReference("DriversAvailable");
        availableDrivers.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot)
            {
                setAvailableDriversList(dataSnapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError)
            {

            }
        });


        //Location loc1 = new Location("");
        //loc1.setLatitude(lat1);
        //loc1.setLongitude(lon1);

        //Location loc2 = new Location("");
        //loc2.setLatitude(lat2);
        //loc2.setLongitude(lon2);

        //float distanceInMeters = loc1.distanceTo(loc2);


        //GeoFire geoFire = new GeoFire(driversLocation);
        //GeoQuery geoQuery = geoFire.queryAtLocation(new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()), distance);
    }

    private void setAvailableDriversList(DataSnapshot dataSnapshot)
    {
        Iterable<DataSnapshot> availDriversChildren = dataSnapshot.getChildren();
        for(DataSnapshot temp : availDriversChildren)
        {
            String id = temp.getKey();
            String lat = temp.child("lat").getValue().toString().trim();
            String lng = temp.child("lng").getValue().toString().trim();
            Driver d = new Driver(id, lat, lng);
            aDList.add(d);
        }

    }*/

}

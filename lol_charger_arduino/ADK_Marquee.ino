

#include "Charliplexing.h"
#include "Myfont.h"

#include "Arduino.h"

/*
 ANDROID ACCESSORY INCLUDES
*/
#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

AndroidAccessory acc("DetroitLabs",
		     "lol charger",
		     "Charge your phone and display messages",
		     "1.0",
		     "http://www.detroitlabs.com",
		     "0000000000000002");

//program consts
const int SYS_MSG_SPEED = 50;
const int DEFAULT_SPEED = 70;
const int HORIZONTAL = 14;
const int VERTICAL = 9;

//commands
const byte IDLE              =  0x01;
const byte CLEAR_BUFFER      =  0x03;
const byte PUSH_TO_BUFFER    =  0x04;
const byte DISPLAY_BUFFER    =  0x05;
const byte SHOW_PERCENT      =  0x06;
const byte CLEAR_DISPLAY     =  0x07;
const byte IS_BUSY           =  0x08;
const byte IS_READY          =  0x09;

//targets
const byte TWEET_BUFFER    =  0x01;
const byte MESSAGE_BUFFER  =  0x02;

boolean isConnected = false;
unsigned char tweetBuffer[144];
unsigned char messageBuffer[256];


//built in messages
unsigned char PWR_ON[]          =  "PWR ON";
unsigned int  PWR_ON_LEN        =  6;
unsigned char CONNECTED[]       =  "Conn";
unsigned int  CONNECTED_LEN     =  3;
unsigned char DISCONNECTED[]    =  "Disco";
unsigned int  DISCONNECTED_LEN  =  5;

int tBufferCount = 0;
int mBufferCount = 0;

void setup()                    // run once, when the sketch starts
{
  Serial.begin(115200);
  LedSign::Init();
  
  unsigned char test[]="READY";
  displayBuffer(PWR_ON, PWR_ON_LEN, SYS_MSG_SPEED);
  acc.powerOn();
}

void loop()
{

  byte msg[3];
  if (acc.isConnected()) {
    
    if (isConnected == false) {
     isConnected = true;
      displayBuffer(CONNECTED, CONNECTED_LEN, SYS_MSG_SPEED); 
    }
    
    int len = acc.read(msg, 3, 1);

    if (len > 0) {
          
     switch(msg[0]) {
       Serial.println(msg[0]);
       case IDLE:
        break;
      case CLEAR_DISPLAY:
        LedSign::Clear();
        break;
      case CLEAR_BUFFER:
        Serial.print("CLEAR");
        if (msg[1] == MESSAGE_BUFFER)
          mBufferCount = 0;
        break;
      case PUSH_TO_BUFFER:
        Serial.print("PUSH: ");
        Serial.println(msg[2]);
        if (msg[1] == MESSAGE_BUFFER)
          messageBuffer[mBufferCount++] = msg[2];
        break;
      case DISPLAY_BUFFER:
        Serial.print("DISPLAY");
        if (msg[1] == MESSAGE_BUFFER)
          displayBuffer(messageBuffer, mBufferCount, DEFAULT_SPEED);
        break;
       case SHOW_PERCENT:
         setPercent(msg[2], HORIZONTAL);
         break;
     } 
    }
    
  } else {
   if (isConnected) {
    isConnected = false;
    displayBuffer(DISCONNECTED, DISCONNECTED_LEN, SYS_MSG_SPEED);
   } 
  }
}

void displayBuffer(unsigned char buffer[], int len, int spdDelay) {
  Myfont::Banner(len, buffer, spdDelay);
}

unsigned char* trimBuffer(unsigned char buffer[], int len) {
  unsigned char temp[len];
  for (int i=0; i < len; i++) {
    temp[i] = buffer[i];
  }
  return temp;
}

void setPercent(double percent, int maximum) {
  Serial.println(percent);
 LedSign::Clear();
 double per = (percent/100)*maximum;
 for (int i=0; i < per; i++) {
  if (maximum == HORIZONTAL) {
   LedSign::Vertical(i,1); 
  } else {
   LedSign::Horizontal(i,1); 
  }
 } 
}

void sendBusyStatus(boolean isBusy) {
 byte msg[3];
 msg[0] = (isBusy) ? IS_BUSY : IS_READY;
 msg[1] = 0x00;
 msg[2] = 0x00;
 acc.write(msg, 3); 
}

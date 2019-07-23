#include <BLE2902.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEUtils.h>
#include <BLEBeacon.h>
#include "SSD1306.h"
#include <stdio.h>
#include <string>
#include <Wire.h>
#include <SPI.h>
#include <Adafruit_Sensor.h>
#include <Adafruit_BME280.h>

// SDA 4
// SCL 5

using namespace std;

// Constants
#define SERVICE_SENSOR_UUID "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define CHARACTERISTIC_TEMPERATURE_UUID "00002a6e-0000-1000-8000-00805f9b34fb"
#define CHARACTERISTIC_HUMIDITY_UUID "00002a6f-0000-1000-8000-00805f9b34fb"
#define SEALEVELPRESSURE_HPA (1013.25)

// Variables
SSD1306 display(0x3c, 5, 4);
BLEServer *pServer = NULL;
BLECharacteristic *temperatureCharacteristic = NULL;
BLECharacteristic *humidityCharacteristic = NULL;
bool deviceConnected = false;
bool oldDeviceConnected = false;
Adafruit_BME280 bme; // I2C

class MyServerCallback : public BLEServerCallbacks
{
  void onConnect(BLEServer *pServer)
  {
    deviceConnected = true;
    // display.init();
    // display.drawString(0, 0, "DEVICE CONNECTED");
    // display.display();
  };

  void onDisconnect(BLEServer *pServer)
  {
    deviceConnected = false;
  }
};

void setup()
{
  Serial.begin(9600);

  while (!Serial)
  {
  } // Wait

  Wire.begin(4, 5);

  bool status;

  status = bme.begin(0x76);
  if (!status)
  {
    Serial.println("Could not find a valid BME280 sensor, check wiring!");
    while (1)
      ;
  }

  // Create the BLE Device
  BLEDevice::init("CC50E380BDA6");

  display.init();
  display.drawString(0, 0, "ESP32");
  display.display();

  // Create the BLE Server
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new MyServerCallback());

  // Create the BLE Service
  BLEService *pService = pServer->createService(SERVICE_SENSOR_UUID);

  // Create a BLE Characteristic
  temperatureCharacteristic = pService->createCharacteristic(CHARACTERISTIC_TEMPERATURE_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  humidityCharacteristic = pService->createCharacteristic(CHARACTERISTIC_HUMIDITY_UUID, BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);

  // Create a BLE Descriptor
  temperatureCharacteristic->addDescriptor(new BLE2902());
  humidityCharacteristic->addDescriptor(new BLE2902());

  // Start the service
  pService->start();

  // Start advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_SENSOR_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);  // functions that help with iPhone connections issue
  pAdvertising->setMinPreferred(0x12);
  BLEDevice::startAdvertising();
  Serial.println("BLE Device started to advertise.");
}

void loop()
{
  Wire.begin(4, 5);

  // float temperature = -20 + static_cast<float>(rand()) / (static_cast<float>(RAND_MAX / (50 - (-20))));
  // float humidity = 20 + static_cast<float>(rand()) / (static_cast<float>(RAND_MAX / (26 - (20))));
  delay(800);
  float temperature = bme.readTemperature();
  float humidity = bme.readHumidity();
  float pressure = bme.readPressure() / 100.0F;

  display.init();
  display.setTextAlignment(TEXT_ALIGN_LEFT);
  display.setFont(ArialMT_Plain_10);
  if (deviceConnected)
  {
    display.drawString(5, 0, "Client connected: Yes");
  }
  else
  {
    display.drawString(5, 0, "Client connected: No");
  }

  display.setFont(ArialMT_Plain_10);
  display.drawString(5, 10, "Temperature: " + String(temperature) + " *C");

  display.setFont(ArialMT_Plain_10);
  display.drawString(5, 20, "Humidity: " + String(humidity) + "%");

  display.setFont(ArialMT_Plain_10);
  display.drawString(5, 30, "Pressure: " + String(pressure) + "hPa");

  display.display();

  // notify changed value
  if (deviceConnected)
  {
    Serial.println("Temperature: " + String(temperature));
    Serial.println("Humidity: " + String(humidity));
    Serial.println("------------------------------------");

    uint8_t temp[4];
    *((float *)temp) = temperature;
    temperatureCharacteristic->setValue(temp, 4);

    uint8_t hum[4];
    *((float *)hum) = humidity;
    humidityCharacteristic->setValue(hum, 4);

    temperatureCharacteristic->notify();
    humidityCharacteristic->notify();
  }
  // disconnecting
  if (!deviceConnected && oldDeviceConnected)
  {
    delay(500);                  // give the bluetooth stack the chance to get things ready
    pServer->startAdvertising(); // restart advertising
    Serial.println("start advertising");
    oldDeviceConnected = deviceConnected;
  }

  // connecting
  if (deviceConnected && !oldDeviceConnected)
  {
    // do stuff here on connecting
    oldDeviceConnected = deviceConnected;
  }
}
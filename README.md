# Demo chat for Android

Work in progress. What works:

* Registering new account
* Loading the list of contacts
* Sending and receiving messages
* Presence notifications
* Message counters
* Indicators of messages received/read (little check marks in messages)

Does not work yet:

* Modifying existing account
* Deleting/muting topics and messages
* No offline mode
* Can't start new conversations
* No push notifications

Dependencies: [jackson](https://github.com/FasterXML/jackson), [nv-websocket-client](https://github.com/TakahikoKawasaki/nv-websocket-client)

The `contacts.vcf` contains a list of contacts which can be used for testing. Push it to your emulator using

  `adb push contacts.vcf /sdcard/contacts.vcf`

<img src="android-contacts.png" alt="App screenshot - contacts" width="270" />
<img src="android-messages.png" alt="App screenshot - contacts" width="270" />

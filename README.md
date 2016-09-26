# Demo chat for Android

Work in progress. What works:

* Register new account, login
* Load the list of contacts
* Sends and receives messages
* Presence notifications work
* Message counters work
* Indicators for messages received/read (little check marks in messages)

Does not work yet:

* Modifying an existing account
* Contact discovery - can't find other people to chat with
* Deleting/muting topics and messages
* No offline mode
* Can't start new conversations
* No push notifications

Dependencies on the SDK side:

* [jackson](https://github.com/FasterXML/jackson) for json serialization
* [nv-websocket-client](https://github.com/TakahikoKawasaki/nv-websocket-client) for websocket support

Dependencies on the application side:

* [libphonenumber](https://github.com/googlei18n/libphonenumber) for user discovery, making sure all phone numbers use the same E.164 format

The `contacts.vcf` contains a list of contacts which can be used for testing. Push it to your emulator using

  `adb push contacts.vcf /sdcard/contacts.vcf`

<img src="android-contacts.png" alt="App screenshot - contacts" width="270" />
<img src="android-messages.png" alt="App screenshot - contacts" width="270" />

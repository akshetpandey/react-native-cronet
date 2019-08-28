# react-native-cronet

[Cronet](https://chromium.googlesource.com/chromium/src/+/master/components/cronet) is the [networking stack of Chromium](https://chromium.googlesource.com/chromium/src/+/master/net/docs/life-of-a-url-request.md) put into a library for use on mobile. 
This is the same networking stack that is used in the Chrome browser by over a billion people. 
It offers an easy-to-use, high performance, standards-compliant, and secure way to perform HTTP requests. 
Cronet has support for both Android and iOS.


This module allows you to use the Cronet stack for your react native apps. 
All you have to do is add this package to your project using yarn or npm.

## NOTE

For iOS, you will have to disable bitcode for your target.

In XCode, in the project navigator, select your project. `Build Settings` ➜ `Enable Bitcode` ➜ `No`

## Getting started

Using npm:

```shell
npm install --save react-native-cronet
```

or using yarn:

```shell
yarn add react-native-cronet
```

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-cronet` and add `RnCronet.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRnCronet.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainApplication.java`
  - Add `import com.akshetpandey.rncronet.RnCronetPackage;` to the imports at the top of the file
  - Add `new RnCronetPackage()` to the list returned by the `getPackages()` method
2. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-cronet'
  	project(':react-native-cronet').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-cronet/android')
  	```
3. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-cronet')
  	```

## Usage

You don't have to do anything else. Cronet is used automatically for all react-native network request
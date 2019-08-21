# react-native-cronet

## Getting started

`$ npm install react-native-cronet --save`

### Mostly automatic installation

`$ react-native link react-native-cronet`

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
```javascript
import RnCronet from 'react-native-cronet';

// TODO: What to do with the module?
RnCronet;
```

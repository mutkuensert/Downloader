# Downloader

## Table of Contents
* [How to Use](#how-to-use)
* [License](#license)
* [Contact](#contact)

## Adding the library
Clone the project.

Run in the terminal:
```
./gradlew build
```
Find generated aar file under build folder and copy & paste it into your project. You can create a directory called "libs" under your app module and paste the file into there.
Then you can add the dependency:

```
implementation files('libs/Downloader.aar')
```

## How to Use
If you use Hilt in your project, you can initialize Downloader class like below. You should also implement a CoroutineScope and inject it.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DownloaderModule {

    @Provides
    fun providesDownloader(scope: CoroutineScope, @ApplicationContext context: Context): Downloader {
        return Downloader(scope = scope, context = context)
    }
}
```
The notification can be edited:
```kotlin
val downloader = Downloader(scope = scope, context = context)
downloader.notificationBuilder.setAutoCancel(false)
```

Inject the downloader somewhere, for example a ViewModel:
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    ...,
    val downloader: Downloader,
) : ViewModel() { }
```
Initialize activity result launcher in a Fragment or an Activity:
```kotlin
viewModel.downloader.initActivityResultLauncher {
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    uri = result.data?.data

                    if (uri != null) this.writeToFile(uri!!)
                }
            }
        }
```

The library uses functions of its own to extract the file name and format from the url.
If a custom extractor is needed different from default ones, you can set different ones:
```kotlin
downloader.setFileFormatExtractor {
            it.substringAfterLast(".").substringBefore("?")
        }
```

You can override onDownloadStart and onDownloadComplete functions by creating a new class inheriting this one.

Finally, you can download the file on the url:
```kotlin
downloader.downloadUrl(url)
```

Notification and internet permissions have to be added in the manifest file.

## License
```xml
Copyright 2022 Mustafa Utku Ensert

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Contact
[ensertyazilim@gmail.com](#)

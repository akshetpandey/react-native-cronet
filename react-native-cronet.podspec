require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "react-native-cronet"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = <<-DESC
Cronet is the networking stack of Chromium put into a library for use on mobile.
This is the same networking stack that is used in the Chrome browser by over a billion people.
It offers an easy-to-use, high performance, standards-compliant, and secure way to perform HTTP requests.
                   DESC
  s.homepage     = "https://github.com/akshetpandey/react-native-cronet"
  s.license      = "MIT"
  s.authors      = { "Akshet Pandey" => "github@akshetpandey.com" }
  s.platform     = :ios, "9.0"
  s.source       = { :git => "https://github.com/akshetpandey/react-native-cronet.git", :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.requires_arc = true

  s.dependency "React"
  s.dependency "Cronet"
end


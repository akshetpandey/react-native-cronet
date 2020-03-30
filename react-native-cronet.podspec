require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = package['name']
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package['homepage']
  s.license      = package['license']
  s.authors      = package['author']
  s.platform     = :ios, "9.0"
  s.source       = { :git => "https://github.com/akshetpandey/react-native-cronet.git", :tag => "v#{s.version}" }

  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.requires_arc = true

  s.dependency "React"
  s.dependency "Cronet"
end

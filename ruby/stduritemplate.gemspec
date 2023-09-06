Gem::Specification.new do |s|
  s.name          = "stduritemplate"
  s.version       = ENV['VERSION'] || "0.0.0"
  s.summary       = "stduritemplate"
  s.description   = "std-uritemplate implementation for Ruby"
  s.authors       = ["Andrea Peruffo"]
  s.email         = "andrea.peruffo1982@gmail.com"
  s.files         = ["stduritemplate.gemspec", "lib/stduritemplate.rb", "Gemfile"]
  s.homepage      = "https://github.com/std-uritemplate/std-uritemplate"
  s.license       = "APACHE-2"
  s.platform      = Gem::Platform::RUBY
  s.require_paths = ['lib']
end

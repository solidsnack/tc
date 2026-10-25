# frozen_string_literal: true

require_relative 'lib/timecode/version'

Gem::Specification.new do |spec|
    spec.name = 'timecode'
    spec.version = TimeCode::VERSION
    spec.summary = 'TimeCodes: sortable, short timestamps'
    spec.description = 'Encodes and parses TimeCodes, short timestamps of ' \
                       'digits and capital letters that sort in time ' \
                       'order, and the bicentennial codes made from them.'
    spec.authors = ['Jason Dusek']
    spec.license = 'Unlicense'
    spec.homepage = 'https://github.com/solidsnack/tc'
    spec.metadata['rubygems_mfa_required'] = 'true'

    spec.required_ruby_version = '>= 3.2'
    spec.files = Dir['lib/**/*.rb', 'README.md']
end

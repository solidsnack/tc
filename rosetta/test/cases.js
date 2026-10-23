// The cases in specification/tests.yaml.
const fs = require('node:fs')
const path = require('node:path')
const { parse } = require('yaml')

const file = path.join(__dirname, '..', '..', 'specification', 'tests.yaml')
const suite = parse(fs.readFileSync(file, 'utf8'))
const groups = ['tc5', 'tc8', 'tcx', 'tcn']

module.exports = { suite, groups }

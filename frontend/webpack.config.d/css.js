config.resolve.modules.push("../frontend/src/jsMain/resources");
//
config.devServer = config.devServer || {}
config.devServer.historyApiFallback = {
    disableDotRule: true,
    index: "index.html"
}
//config.devServer.port = 8080

config.module.rules.push({
    test: /\.css$/,
    use: ["style-loader", "css-loader"]
});

config.module.rules.push({
    test: /\.(woff(2)?|ttf|eot|svg|gif|png|jpe?g)(\?v=\d+\.\d+\.\d+)?$/,
    use: [{
        loader: 'file-loader',
        options: {
            name: '[name].[ext]',
            outputPath: 'fonts/'
        }
    }]
});

//path = require("path")
//module.exports = {
//    name: 'server',
//    entry: {
//        main: path.resolve(__dirname, "kotlin\\kindex-browser.js")
//    },
//    target: 'web',
//    output: {
//        path: './build/bundle',
//        filename: 'bundle.js'
//    },
//    mode: 'production'
//}
delete config.output.library;
delete config.output.libraryTarget;

console.info('Done Loading Webpack config..');


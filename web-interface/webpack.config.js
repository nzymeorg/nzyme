var path = require('path');
const HtmlWebPackPlugin = require("html-webpack-plugin");

const htmlPlugin = new HtmlWebPackPlugin({
  template: "./src/index.html",
  filename: "./index.html"
});

module.exports = {
  mode: "development",
  module: {
    rules: [
      {
        test: /\.js$|jsx/,
        exclude: /node_modules/,
        resolve: {
          extensions: [".js", ".jsx"]
        },
        use: {
          loader: "babel-loader"
        }
      },
      {
        test: /\.css$/,
        use: ["style-loader", "css-loader"]
      }
    ],
  },
  output: {
    path: path.resolve(__dirname, 'build'),
    filename: 'index_bundle.js',
    publicPath: '/',
    filename: 'static/js/[name].[chunkhash:8].js',
    chunkFilename: 'static/js/[name].[chunkhash:8].chunk.js'
  },
  devServer: {
    historyApiFallback: {
      disableDotRule: true
    },
    client: {
      overlay: true
    }
  },
  plugins: [
    htmlPlugin
  ],
};

# supreme-uploading

至高のアップロード機能づくり

## Synopsis

業務システムにおいて、Excelファイルをアップロードしてデータベースに取り込むという機能が求められることがよくあります。

その仕組みをあれこれ考えはじめると…

- 結構なサイズになるので、非同期で処理する
    - 完了したときの通知方法は?
    - エラーレコードの修正方法は?
    - アップロードしたけど間違いに気づいたときにキャンセルする方法は?
- ファイルを一時でも保存するのであれば、ウィルスチェックもやらなきゃいけない。

など、かなり大がかりなことになりそうです。

2015年現在のテクノロジーでなんとかならないのでしょうか? というチャレンジです。

## The patterns of uploading

### A. Parse XLSX at a client-side

クライアントサイドでExcelを読むパターンです。
js-xlsx ( https://github.com/SheetJS/js-xlsx )を使い、JavascriptだけでExcelをパースします。

1. ファイルを指定して読み込みます。
   ![Imgur](http://i.imgur.com/a40Apdf.png)
2. 読み取ったレコードをサーバにPOSTし、バリデーションエラーになったものは修正フォームを表示します。
   ![Imgur](http://i.imgur.com/02pBUl4.png)
3. エラーを修正して完了です。


### B. Copy & paste

そもそもアップロードするから面倒なわけで、グリッドにコピペで済ますパターンです。

Handsontable ( http://handsontable.com/ )を使い、ローカルのExcelからコピーしグリッドにペーストさせます。

1. 貼付け用のグリッドを表示します。
   ![Imgur](http://i.imgur.com/UhJ3CrL.png)
2. Excelからデータをコピーします。
   ![Imgur](http://i.imgur.com/STeVlPV.png)
3. グリッドに貼り付け、エラーを修正し保存します。
   ![Imgur](http://i.imgur.com/kXN9YOR.png)

## License

This software is released under the MIT License.

Copyright © 2015 kawasima

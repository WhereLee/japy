import pymysql

conn = pymysql.connect(host='localhost', user='root', password='root', database='japy', charset='utf8mb4')
cur = conn.cursor()

cur.execute("UPDATE novel SET title=%s, author=%s WHERE id=1", ('龙族2·悼亡者之瞳', '江南'))
cur.execute("UPDATE novel SET title=%s, author=%s WHERE id=2", ('天龙八部（世纪新修版）', '金庸'))
cur.execute("UPDATE novel SET title=%s, author=%s WHERE id=3", ('斗破苍穹', '天蚕土豆'))
conn.commit()

cur.execute('SELECT id, title, author FROM novel')
for row in cur.fetchall():
    print(row)

conn.close()
print('done')

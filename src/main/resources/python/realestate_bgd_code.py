import psycopg2
import pandas as pd

# 1. DB 연결 설정
conn = psycopg2.connect(
    host="localhost",
    port=5432,
    dbname="realestate",
    user="dev",
    password="dev1234"
)

# 2. 올바른 SQL 구문 (컬럼 2개, 파라미터 2개 일치)
sql = """
INSERT INTO bgd_code (bgd_code, bgd_name)
VALUES (%s, %s)
"""

print("1. 엑셀 파일 로드 중...")
try:
    # Pandas를 이용해 .xlsx 파일을 읽으면 인코딩(cp949) 고민을 안 하셔도 됩니다.
    # 만약 파일명이 아까처럼 '_data'가 붙어있다면 파일 이름을 정확히 수정해주세요!
    df = pd.read_excel("realestate_bgd_code.xlsx")
    
    # 엑셀의 첫 번째, 두 번째 컬럼 데이터를 튜플 리스트로 변환
    # (iloc[:, 0]은 첫 번째 열, iloc[:, 1]은 두 번째 열을 의미합니다)
    data = list(zip(df.iloc[:, 0], df.iloc[:, 1]))
    
except FileNotFoundError:
    print("❌ 에러: 'realestate_bgd_code.xlsx' 파일을 찾을 수 없습니다. 파일명을 확인해주세요.")
    conn.close()
    exit()

print(f"2. 데이터 파싱 완료 (총 {len(data)}건). DB 적재 시작...")

# 3. DB에 대량 인서트 실행
try:
    with conn.cursor() as cur:
        cur.executemany(sql, data)
    
    conn.commit()
    print(f"🎉 {len(data)}건 INSERT 완료!")
    print("첫 번째 데이터 확인:", data[0] if data else "없음")

except Exception as e:
    conn.rollback()
    print(f"❌ DB 반영 중 에러 발생 (롤백됨): {e}")

finally:
    conn.close()